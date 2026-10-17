import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import util.*;
import service.*;
import repository.*;

/** Real PostgreSQL + HTTP integration, isolated to test databases, no demo records removed. */
public class RecruitmentIntegrationTest {
    static HttpClient client=HttpClient.newHttpClient();
    static String origin,cookie="";
    static List<Integer> jobs=new ArrayList<>();
    static Integer desktopId;
    static String namedAccount;
    static HttpResponse<byte[]> request(String path,String method,byte[] body,Map<String,String> headers)throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(origin+path)).timeout(java.time.Duration.ofSeconds(20));
        headers.forEach(builder::header);
        return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    static HttpResponse<byte[]> get(String path,boolean admin)throws Exception{return request(path,"GET",null,admin?Map.of("Cookie",cookie):Map.of());}
    static HttpResponse<byte[]> post(String path,Map<String,String> data,boolean admin)throws Exception {
        StringJoiner form=new StringJoiner("&");data.forEach((k,v)->form.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
        Map<String,String> headers=new HashMap<>(Map.of("Origin",origin,"Content-Type","application/x-www-form-urlencoded"));if(admin)headers.put("Cookie",cookie);
        return request(path,"POST",form.toString().getBytes(StandardCharsets.UTF_8),headers);
    }
    static HttpResponse<byte[]> upload(int job,String email,byte[] pdf)throws Exception {
        return request("/api/apply","POST",pdf,Map.of("Origin",origin,"Content-Type","application/pdf","X-Application-job",String.valueOf(job),"X-Application-name",URLEncoder.encode("Test Candidate",StandardCharsets.UTF_8),"X-Application-email",email,"X-Application-filename","cv.pdf"));
    }
    static String text(HttpResponse<byte[]> r){return new String(r.body(),StandardCharsets.UTF_8);}
    static String field(HttpResponse<byte[]> r,String key){Matcher m=Pattern.compile("\""+key+"\"\\s*:\\s*(?:\"([^\"]*)\"|(\\d+))").matcher(text(r));if(!m.find())throw new AssertionError(text(r));return m.group(1)!=null?m.group(1):m.group(2);}
    static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);}
    static byte[] pdf(boolean active)throws Exception {
        try(PDDocument document=new PDDocument();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            document.addPage(new PDPage());if(active)document.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('test')"));document.save(out);return out.toByteArray();
        }
    }
    static int job(boolean active)throws Exception {
        var result=post("/api/admin/jobs",Map.of("company","Integration Test","title","Test Developer","location","Berlin","description","Test vacancy","active",String.valueOf(active)),true);
        check(result.statusCode()==200,"Create job: "+text(result));int id=Integer.parseInt(field(result,"id"));jobs.add(id);return id;
    }
    public static void main(String[] args)throws Exception {
        String url=System.getenv("DB_URL");if(url==null || !url.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_test"))throw new IllegalArgumentException("Use a separate _test database");
        origin=System.getenv().getOrDefault("PUBLIC_ORIGIN","http://localhost:"+System.getenv().getOrDefault("PORT","8080"));
        try {
            main.WebMain.main(new String[0]);
            SchemaMigrator.migrate(); // repeated startup remains safe
            check(text(get("/admin",false)).contains("Company login"),"Login form");
            check(get("/api/admin/applications",false).statusCode()==401,"Protected applicant list");
            var login=post("/api/login",Map.of("username","admin","password",System.getenv("ADMIN_PASSWORD")),false);
            check(login.statusCode()==200,"Login: "+text(login));cookie=login.headers().firstValue("Set-Cookie").orElseThrow().split(";",2)[0];
            check(login.headers().firstValue("Set-Cookie").orElseThrow().contains("HttpOnly"),"HttpOnly session");
            namedAccount="test_"+UUID.randomUUID().toString().replace("-","");
            String namedPassword=" named-test-password-2026 ";
            AuthService.createAccount(namedAccount,namedPassword,false);
            var namedLogin=post("/api/login",Map.of("username",namedAccount,"password",namedPassword),false);
            check(namedLogin.statusCode()==200,"Named account and exact password handling");
            try(Connection c=DatabaseConnection.getConnection();PreparedStatement s=c.prepareStatement("SELECT password_hash FROM admin_accounts WHERE username=?")) {
                s.setString(1,namedAccount);try(ResultSet r=s.executeQuery()){r.next();check(!r.getString(1).equals(namedPassword) && r.getString(1).matches("[^:]+:[0-9a-f]{64}"),"Salted password hash");}
            }
            check(text(get("/admin",true)).contains("desktopRecords"),"Unified dashboard");
            int closed=job(false),open=job(true);
            check(!text(get("/api/jobs",false)).contains("\"id\":"+closed+","),"New closed vacancy stays private");
            byte[] pdf=pdf(false);
            var applied=upload(open,"candidate@example.test",pdf);check(applied.statusCode()==201,"Apply: "+text(applied));
            int application=Integer.parseInt(field(applied,"id"));String tracking=field(applied,"trackingToken");
            check(upload(open,"candidate@example.test",pdf).statusCode()==409,"Duplicate rejected");
            check(upload(closed,"candidate@example.test",pdf).statusCode()==400,"Closed job rejected");
            check(upload(open,"bad@example.test","%PDF-1.4\nFake".getBytes()).statusCode()==400,"Fake PDF rejected");
            check(upload(open,"active@example.test",pdf(true)).statusCode()==400,"Active PDF rejected");
            check(upload(open,"large@example.test",new byte[RecruitmentService.MAX_CV+1]).statusCode()==400,"Oversized PDF rejected");
            check(get("/api/admin/cv/"+application,false).statusCode()==401,"Private CV");
            check(Arrays.equals(get("/api/admin/cv/"+application,true).body(),pdf),"CV bytes preserved");
            check(post("/api/admin/status",Map.of("id",String.valueOf(application),"status","INTERVIEW"),true).statusCode()==200,"Status update");
            var tracked=post("/api/track",Map.of("token",tracking),false);
            check(text(tracked).contains("INTERVIEW") && !text(tracked).contains("email") && !text(tracked).contains("cv"),"Private tracking excludes sensitive data");
            check(post("/api/track",Map.of("token",UUID.randomUUID().toString()+UUID.randomUUID()),false).statusCode()==404,"Unknown token rejected");
            var old=new JobApplicationService(new JobApplicationRepository()).addApplication("Unified test","Intern","Berlin");desktopId=old.getId();
            check(text(get("/api/admin/desktop",true)).contains("Unified test"),"Desktop records visible");
            check(post("/api/admin/desktop/details",Map.of("id",String.valueOf(desktopId),"company","Unified test edited","title","Developer","location","Hamburg"),true).statusCode()==200,"Desktop edit through web");
            check(new JobApplicationRepository().findById(desktopId).getLocation().equals("Hamburg"),"Desktop edit persisted");
            for(int i=0;i<21;i++)job(false);
            check(text(get("/api/admin/jobs?page=1",true)).contains("\"hasMore\":true"),"First page has more");
            check(get("/api/admin/jobs?page=2",true).statusCode()==200,"Second page");
            check(request("/api/logout","POST",new byte[0],Map.of("Origin","https://evil.example","Cookie",cookie)).statusCode()==403,"Cross-origin mutation blocked");
            check(post("/api/logout",Map.of(),true).statusCode()==200,"Logout");
            check(get("/api/admin/jobs",true).statusCode()==401,"Logged-out cookie revoked");
            for(int i=0;i<5;i++)check(post("/api/login",Map.of("username","admin","password","wrong-password"),false).statusCode()==401,"Bad password rejected");
            check(post("/api/login",Map.of("username","admin","password",System.getenv("ADMIN_PASSWORD")),false).statusCode()==429,"Login throttling");
            System.out.println("PASS: migrations, cookie login/logout, throttling, job visibility/pagination, binary CVs, malformed/active/oversized PDFs, duplicates, private tracking, status and unified desktop editing.");
        } finally {
            main.WebMain.stop();
            try(Connection c=DatabaseConnection.getConnection()) {
                if(namedAccount!=null)try(PreparedStatement s=c.prepareStatement("DELETE FROM admin_accounts WHERE username=?")){s.setString(1,namedAccount);s.executeUpdate();}
                for(int job:jobs){try(PreparedStatement s=c.prepareStatement("DELETE FROM candidate_applications WHERE job_id=?")){s.setInt(1,job);s.executeUpdate();}try(PreparedStatement s=c.prepareStatement("DELETE FROM job_postings WHERE id=?")){s.setInt(1,job);s.executeUpdate();}}
            }
            if(desktopId!=null)new JobApplicationRepository().deleteById(desktopId);
        }
    }
}
