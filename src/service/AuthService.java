package service;

import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import util.DatabaseConnection;

/** Named accounts, salted PBKDF2 hashes, revocable sessions and login throttling. */
public final class AuthService {
    private record Session(String username,long expires) {}
    private record Attempts(int count,long since) {}
    private final Map<String,Session> sessions=new ConcurrentHashMap<>();
    private final Map<String,Attempts> attempts=new ConcurrentHashMap<>();
    private static final long WINDOW=15*60*1000L;
    public AuthService() throws Exception {
        String password=System.getenv("ADMIN_PASSWORD");
        if(password!=null && !password.isBlank()) createAccount("admin",password,false);
        try(Connection c=DatabaseConnection.getConnection(); Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT count(*) FROM admin_accounts WHERE active")) {
            r.next(); if(r.getInt(1)==0) throw new IllegalArgumentException("Set ADMIN_PASSWORD (12+ characters) to bootstrap the first account");
        }
    }
    public static void createAccount(String username,String password,boolean replace) throws Exception {
        if(!username.matches("[a-zA-Z0-9._-]{3,50}") || password.length()<12 || password.length()>200) throw new IllegalArgumentException("Username: 3–50 letters/numbers; password: 12–200 characters");
        String salt=UUID.randomUUID().toString(); String hash=salt+":"+derive(password,salt);
        String conflict=replace?"DO UPDATE SET password_hash=excluded.password_hash,active=true":"DO NOTHING";
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement s=c.prepareStatement("INSERT INTO admin_accounts(username,password_hash) VALUES(?,?) ON CONFLICT(username) "+conflict)) {
            s.setString(1,username);s.setString(2,hash);s.executeUpdate();
        }
    }
    public synchronized String login(String username,String password,String address) throws Exception {
        long now=System.currentTimeMillis();
        attempts.entrySet().removeIf(e->now-e.getValue().since()>WINDOW);
        sessions.entrySet().removeIf(e->e.getValue().expires()<now);
        Attempts attempt=attempts.get(address);
        if((attempt!=null && attempt.count()>=5) || attempts.size()>=10000) throw new LoginException(429,"Too many attempts. Try again in 15 minutes.");
        boolean valid;
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement s=c.prepareStatement("SELECT password_hash FROM admin_accounts WHERE username=? AND active")) {
            s.setString(1,username);try(ResultSet r=s.executeQuery()) {
                String stored=r.next()?r.getString(1):"dummy:"+"0".repeat(64);
                String[] parts=stored.split(":",2);
                valid=MessageDigest.isEqual(derive(password,parts[0]).getBytes(java.nio.charset.StandardCharsets.US_ASCII),parts[1].getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
        }
        if(!valid) {attempts.put(address,new Attempts(attempt==null?1:attempt.count()+1,attempt==null?now:attempt.since()));throw new LoginException(401,"Incorrect username or password");}
        attempts.remove(address);
        if(sessions.size()>=10000) throw new LoginException(503,"Session capacity reached");
        String token=UUID.randomUUID().toString()+UUID.randomUUID();
        sessions.put(token,new Session(username,now+8*60*60*1000L));return token;
    }
    public boolean authenticated(String cookie) {
        String token=token(cookie); Session session=sessions.get(token);
        if(session==null)return false;
        if(session.expires()<System.currentTimeMillis()){sessions.remove(token);return false;}return true;
    }
    public void logout(String cookie) {sessions.remove(token(cookie));}
    private static String token(String cookie) {
        if(cookie!=null)for(String part:cookie.split(";")){String[] pair=part.strip().split("=",2);if(pair.length==2 && pair[0].equals("company_session"))return pair[1];}return "";
    }
    private static String derive(String password,String salt) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt.getBytes(java.nio.charset.StandardCharsets.UTF_8),210000,256);
        try{return HexFormat.of().formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());}finally{spec.clearPassword();}
    }
    public static final class LoginException extends Exception {
        public final int status;
        public LoginException(int status,String message){super(message);this.status=status;}
    }
}
