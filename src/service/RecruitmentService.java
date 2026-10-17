package service;

import java.util.*;
import java.sql.SQLException;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import repository.RecruitmentRepository;

public final class RecruitmentService {
    public static final int MAX_CV = 5*1024*1024;
    private final RecruitmentRepository repository = new RecruitmentRepository();
    public Map<String,Object> jobs(boolean all, int page, String search) throws SQLException {return paged(repository.jobs(all,page,search),page);}
    public Map<String,Object> applications(int page) throws SQLException {return paged(repository.applications(page),page);}
    public Map<String,Object> desktop(int page) throws SQLException {return paged(repository.desktop(page),page);}
    public int saveJob(Map<String,String> f) throws SQLException {
        Integer id = f.getOrDefault("id", "").isBlank() ? null : positive(f.get("id"));
        String active = f.getOrDefault("active", "true");
        if (!Set.of("true","false").contains(active)) throw new IllegalArgumentException("Invalid job visibility");
        return repository.saveJob(id,required(f,"company",200),required(f,"title",200),required(f,"location",200),required(f,"description",10000),Boolean.parseBoolean(active));
    }
    public Map<String,Object> apply(Map<String,String> f, byte[] cv) throws SQLException {
        int job = positive(required(f,"job",20));
        String name = required(f,"name",200), email = required(f,"email",254).toLowerCase(Locale.ROOT), filename=required(f,"filename",255);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Enter a valid email");
        validatePdf(filename,cv);
        String token = UUID.randomUUID().toString()+UUID.randomUUID();
        return Map.of("id",repository.apply(job,name,email,filename,cv,token),"trackingToken",token);
    }
    public Map<String,Object> track(String token) throws SQLException {
        if (token==null || !token.matches("[a-f0-9-]{72}")) throw new IllegalArgumentException("Invalid tracking code");
        return repository.track(token);
    }
    public void status(Map<String,String> f) throws SQLException {
        String status = required(f,"status",20);
        if (!Set.of("APPLIED","INTERVIEW","REJECTED","OFFER").contains(status)) throw new IllegalArgumentException("Invalid status");
        repository.status(positive(f.get("id")),status);
    }
    public byte[] cv(int id) throws SQLException {return repository.cv(id);}
    public static void validatePdf(String filename, byte[] bytes) {
        if(bytes.length<5 || bytes.length>MAX_CV || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) throw new IllegalArgumentException("CV must be a PDF, maximum 5 MB");
        try (PDDocument document=Loader.loadPDF(bytes)) {
            if(document.isEncrypted() || document.getNumberOfPages()<1 || document.getNumberOfPages()>100) throw new IllegalArgumentException("Use an unencrypted PDF with 1–100 pages");
            // Walk dictionaries and arrays: reject executable actions and attachments anywhere in the document.
            Set<COSBase> visited=Collections.newSetFromMap(new IdentityHashMap<>());
            inspect(document.getDocument().getTrailer(),visited);
            inspect(document.getDocumentCatalog().getCOSObject(),visited);
        } catch(IOException ex) {throw new IllegalArgumentException("The PDF is damaged, encrypted or unreadable");}
    }
    private static void inspect(COSBase value,Set<COSBase> visited) {
        inspect(value,visited,0);
    }
    private static void inspect(COSBase value,Set<COSBase> visited,int depth) {
        if(value==null || !visited.add(value)) return;
        if(visited.size()>100000 || depth>200) throw new IllegalArgumentException("PDF is too complex");
        if(value instanceof COSObject object) inspect(object.getObject(),visited,depth+1);
        else if(value instanceof COSDictionary dictionary) {
            for(COSName key:dictionary.keySet()) {
                if(Set.of("JS","JavaScript","AA","OpenAction","EmbeddedFiles","EF","XFA","RichMediaContent").contains(key.getName())) throw new IllegalArgumentException("PDF contains active content or attachments; export a plain PDF");
                COSBase item=dictionary.getDictionaryObject(key);
                if(key.getName().equals("S") && item instanceof COSName name && Set.of("JavaScript","Launch","SubmitForm","ImportData","GoToR").contains(name.getName())) throw new IllegalArgumentException("PDF contains executable actions");
                inspect(item,visited,depth+1);
            }
        } else if(value instanceof COSArray array) for(COSBase item:array) inspect(item,visited,depth+1);
    }
    private static Map<String,Object> paged(List<Map<String,Object>> rows,int page) {
        return Map.of("items",rows.subList(0,Math.min(20,rows.size())),"page",page,"hasMore",rows.size()>20);
    }
    public static int positive(String value) {try {int number=Integer.parseInt(value);if(number>0 && number<=1000000)return number;}catch(Exception ignored){}throw new IllegalArgumentException("Invalid ID or page");}
    public static String required(Map<String,String> f,String key,int max) {String value=f.get(key);if(value==null || value.isBlank() || value.length()>max)throw new IllegalArgumentException("Invalid "+key);return value.strip();}
}

