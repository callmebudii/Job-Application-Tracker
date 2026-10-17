package main;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import service.*;
import util.*;

/** Configuration and HTTP routing; validation and SQL live in their own layers. */
public final class WebMain {
    private static final RecruitmentService service=new RecruitmentService();
    private static AuthService auth;
    private static String origin;
    private static HttpServer server;
    private static ExecutorService executor;
    public static synchronized void main(String[] args) throws Exception {
        SchemaMigrator.migrate();auth=new AuthService();
        int port=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
        origin=System.getenv().getOrDefault("PUBLIC_ORIGIN","http://localhost:"+port);
        URI configured=URI.create(origin);
        if(configured.getHost()==null || configured.getRawQuery()!=null || configured.getFragment()!=null || !Set.of("http","https").contains(configured.getScheme()) || !Set.of("", "/").contains(configured.getPath()))throw new IllegalArgumentException("PUBLIC_ORIGIN must be a web origin without a path");
        if(origin.endsWith("/"))origin=origin.substring(0,origin.length()-1);
        if(!configured.getHost().equals("localhost") && !configured.getHost().equals("127.0.0.1") && !configured.getScheme().equals("https"))throw new IllegalArgumentException("Public deployments require an HTTPS PUBLIC_ORIGIN");
        server=HttpServer.create(new InetSocketAddress(System.getenv().getOrDefault("BIND_ADDRESS","127.0.0.1"),port),64);
        executor=new ThreadPoolExecutor(8,8,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(executor);server.createContext("/",WebMain::handle);server.start();
        System.out.println("Applicants: "+origin+"/ | Company: "+origin+"/admin");
    }
    public static synchronized void stop(){if(server!=null)server.stop(0);if(executor!=null)executor.shutdownNow();}
    private static void handle(HttpExchange e) {
        try {
            e.getResponseHeaders().set("X-Content-Type-Options","nosniff");
            e.getResponseHeaders().set("Cache-Control","no-store");
            e.getResponseHeaders().set("Referrer-Policy","no-referrer");
            e.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'");
            String path=e.getRequestURI().getPath(),method=e.getRequestMethod();
            if(!Set.of("GET","POST").contains(method)){json(e,405,Map.of("error","Method not allowed"));return;}
            if(method.equals("POST") && !origin.equals(e.getRequestHeaders().getFirst("Origin"))){json(e,403,Map.of("error","Invalid origin"));return;}
            boolean loggedIn=auth.authenticated(e.getRequestHeaders().getFirst("Cookie"));
            if(path.startsWith("/api/admin/") && !loggedIn){json(e,401,Map.of("error","Company login required"));return;}
            if(method.equals("GET") && Set.of("/","/admin","/app.js","/login.js","/style.css").contains(path)) {
                String file=path.equals("/")?"index.html":path.equals("/admin")?(loggedIn?"index.html":"login.html"):path.substring(1);
                send(e,200,file.endsWith("js")?"text/javascript; charset=utf-8":file.endsWith("css")?"text/css; charset=utf-8":"text/html; charset=utf-8",Files.readAllBytes(Path.of("web",file)));return;
            }
            if(path.equals("/api/login") && method.equals("POST")) {
                Map<String,String> f=form(e);
                RecruitmentService.required(f,"password",200);
                String token=auth.login(RecruitmentService.required(f,"username",50),f.get("password"),e.getRemoteAddress().getAddress().getHostAddress());
                cookie(e,token,28800);json(e,200,Map.of());return;
            }
            if(path.equals("/api/logout") && method.equals("POST")) {auth.logout(e.getRequestHeaders().getFirst("Cookie"));cookie(e,"",0);json(e,200,Map.of());return;}
            Map<String,String> query=decode(e.getRequestURI().getRawQuery());
            int page=query.containsKey("page")?RecruitmentService.positive(query.get("page")):1;
            if(path.equals("/api/jobs") && method.equals("GET"))json(e,200,service.jobs(false,page,query.getOrDefault("search","")));
            else if(path.equals("/api/admin/jobs") && method.equals("GET"))json(e,200,service.jobs(true,page,query.getOrDefault("search","")));
            else if(path.equals("/api/admin/jobs") && method.equals("POST"))json(e,200,Map.of("id",service.saveJob(form(e))));
            else if(path.equals("/api/apply") && method.equals("POST")) {
                if(!"application/pdf".equals(e.getRequestHeaders().getFirst("Content-Type")))throw new IllegalArgumentException("Upload a PDF file");
                Map<String,String> f=new HashMap<>();
                for(String key:List.of("job","name","email","filename")) {
                    String value=e.getRequestHeaders().getFirst("X-Application-"+key);
                    if(value!=null)f.put(key,URLDecoder.decode(value,StandardCharsets.UTF_8));
                }
                json(e,201,service.apply(f,body(e,RecruitmentService.MAX_CV)));
            }
            else if(path.equals("/api/track") && method.equals("POST")) {
                Map<String,Object> result=service.track(form(e).get("token"));
                json(e,result==null?404:200,result==null?Map.of("error","Application not found"):result);
            }
            else if(path.equals("/api/admin/applications") && method.equals("GET"))json(e,200,service.applications(page));
            else if(path.equals("/api/admin/desktop") && method.equals("GET"))json(e,200,service.desktop(page));
            else if(path.equals("/api/admin/desktop/details") && method.equals("POST")) {
                Map<String,String> f=form(e);
                new JobApplicationService(new repository.JobApplicationRepository()).editApplicationDetails(RecruitmentService.positive(f.get("id")),RecruitmentService.required(f,"company",200),RecruitmentService.required(f,"title",200),RecruitmentService.required(f,"location",200));
                json(e,200,Map.of());
            }
            else if(path.equals("/api/admin/desktop/status") && method.equals("POST")) {
                Map<String,String> f=form(e);
                new JobApplicationService(new repository.JobApplicationRepository()).changeApplicationStatus(RecruitmentService.positive(f.get("id")),model.ApplicationStatus.valueOf(RecruitmentService.required(f,"status",20)));
                json(e,200,Map.of());
            }
            else if(path.equals("/api/admin/status") && method.equals("POST")){service.status(form(e));json(e,200,Map.of());}
            else if(path.startsWith("/api/admin/cv/") && method.equals("GET")) {
                int id=RecruitmentService.positive(path.substring("/api/admin/cv/".length()));byte[] cv=service.cv(id);
                if(cv==null){json(e,404,Map.of("error","CV not found"));return;}
                e.getResponseHeaders().set("Content-Disposition","attachment; filename=\"cv-"+id+".pdf\"");send(e,200,"application/pdf",cv);
            }
            else json(e,404,Map.of("error","Not found"));
        } catch(exception.ApplicationNotFoundException ex){error(e,404,"Desktop record not found");}
        catch(AuthService.LoginException ex){error(e,ex.status,ex.getMessage());}
        catch(IllegalArgumentException ex){error(e,400,ex.getMessage());}
        catch(SQLException ex){System.err.println("Database request failed; SQLState="+ex.getSQLState());error(e,"23505".equals(ex.getSQLState())?409:503,"23505".equals(ex.getSQLState())?"You already applied to this job using this email":"Database unavailable. Try again later.");}
        catch(Exception ex){System.err.println("Request failed: "+ex.getClass().getSimpleName());error(e,500,"Request failed");}
        finally{e.close();}
    }
    private static void cookie(HttpExchange e,String token,int maxAge){e.getResponseHeaders().set("Set-Cookie","company_session="+token+"; Path=/; HttpOnly; SameSite=Strict; Max-Age="+maxAge+(origin.startsWith("https:")?"; Secure":""));}
    private static Map<String,String> form(HttpExchange e)throws Exception {
        String type=e.getRequestHeaders().getFirst("Content-Type");
        if(type==null || !type.startsWith("application/x-www-form-urlencoded"))throw new IllegalArgumentException("Invalid form type");
        return decode(new String(body(e,65536),StandardCharsets.UTF_8));
    }
    private static byte[] body(HttpExchange e,int max)throws Exception{byte[] bytes=e.getRequestBody().readNBytes(max+1);if(bytes.length>max)throw new IllegalArgumentException("Upload too large");return bytes;}
    private static Map<String,String> decode(String value){Map<String,String> result=new HashMap<>();if(value!=null)for(String pair:value.split("&")){String[] p=pair.split("=",2);result.put(URLDecoder.decode(p[0],StandardCharsets.UTF_8),URLDecoder.decode(p.length==2?p[1]:"",StandardCharsets.UTF_8));}return result;}
    private static void json(HttpExchange e,int status,Object body)throws java.io.IOException{send(e,status,"application/json; charset=utf-8",Json.encode(body).getBytes(StandardCharsets.UTF_8));}
    private static void error(HttpExchange e,int status,String message){try{json(e,status,Map.of("error",message==null?"Request failed":message));}catch(Exception ignored){}}
    private static void send(HttpExchange e,int status,String type,byte[] bytes)throws java.io.IOException{e.getResponseHeaders().set("Content-Type",type);e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);}
}
