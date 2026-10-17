package repository;

import java.sql.*;
import java.util.*;
import util.DatabaseConnection;

/** SQL only: callers own business validation and HTTP behavior. */
public final class RecruitmentRepository {
    public List<Map<String,Object>> jobs(boolean all, int page, String search) throws SQLException {
        return query("SELECT id,company,title,location,description,active FROM job_postings WHERE (? OR active) AND (company ILIKE ? OR title ILIKE ? OR location ILIKE ?) ORDER BY id DESC LIMIT 21 OFFSET ?",
                all, "%"+search+"%", "%"+search+"%", "%"+search+"%", (page-1)*20);
    }
    public int saveJob(Integer id, String company, String title, String location, String description, boolean active) throws SQLException {
        String sql = id == null
                ? "INSERT INTO job_postings(company,title,location,description,active) VALUES(?,?,?,?,?) RETURNING id"
                : "UPDATE job_postings SET company=?,title=?,location=?,description=?,active=? WHERE id=? RETURNING id";
        Object[] params = id == null ? new Object[]{company,title,location,description,active} : new Object[]{company,title,location,description,active,id};
        List<Map<String,Object>> rows = query(sql, params);
        if (rows.isEmpty()) throw new IllegalArgumentException("Job not found");
        return ((Number)rows.get(0).get("id")).intValue();
    }
    public int apply(int job, String name, String email, String filename, byte[] cv, String token) throws SQLException {
        List<Map<String,Object>> rows = query("INSERT INTO candidate_applications(job_id,candidate_name,email,cv_name,cv_data,tracking_token) SELECT id,?,?,?,?,? FROM job_postings WHERE id=? AND active RETURNING id",
                name,email,filename,cv,token,job);
        if (rows.isEmpty()) throw new IllegalArgumentException("This job is no longer open");
        return ((Number)rows.get(0).get("id")).intValue();
    }
    public List<Map<String,Object>> applications(int page) throws SQLException {
        return query("SELECT a.id,a.candidate_name AS name,a.email,a.cv_name AS filename,a.status,a.created_at AS date,j.title FROM candidate_applications a JOIN job_postings j ON j.id=a.job_id ORDER BY a.id DESC LIMIT 21 OFFSET ?",(page-1)*20);
    }
    public List<Map<String,Object>> desktop(int page) throws SQLException {
        return query("SELECT id,company_name AS company,position AS title,location,status FROM job_applications ORDER BY id DESC LIMIT 21 OFFSET ?",(page-1)*20);
    }
    public Map<String,Object> track(String token) throws SQLException {
        List<Map<String,Object>> rows = query("SELECT a.id,a.status,a.created_at AS date,j.title,j.company FROM candidate_applications a JOIN job_postings j ON j.id=a.job_id WHERE a.tracking_token=?",token);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public void status(int id, String status) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement s = c.prepareStatement("UPDATE candidate_applications SET status=? WHERE id=?")) {
            s.setString(1,status); s.setInt(2,id);
            if (s.executeUpdate()!=1) throw new IllegalArgumentException("Application not found");
        }
    }
    public byte[] cv(int id) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement s = c.prepareStatement("SELECT cv_data FROM candidate_applications WHERE id=?")) {
            s.setInt(1,id); try (ResultSet r = s.executeQuery()) {return r.next() ? r.getBytes(1) : null;}
        }
    }
    private List<Map<String,Object>> query(String sql, Object... params) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i=0;i<params.length;i++) s.setObject(i+1,params[i]);
            try (ResultSet r = s.executeQuery()) {
                List<Map<String,Object>> result = new ArrayList<>();
                ResultSetMetaData metadata = r.getMetaData();
                while (r.next()) {
                    Map<String,Object> row = new LinkedHashMap<>();
                    for(int i=1;i<=metadata.getColumnCount();i++) {
                        Object value = r.getObject(i);
                        row.put(metadata.getColumnLabel(i),value instanceof Timestamp ? value.toString() : value);
                    }
                    result.add(row);
                }
                return result;
            }
        }
    }
}
