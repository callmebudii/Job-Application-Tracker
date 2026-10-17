package main;
import service.AuthService;
import util.SchemaMigrator;
public final class AdminMain {
    public static void main(String[] args) throws Exception {
        if(args.length!=1 || System.console()==null)throw new IllegalArgumentException("Run in a terminal: main.AdminMain USERNAME");
        char[] password=System.console().readPassword("New password (12+ characters): ");
        try{SchemaMigrator.migrate();AuthService.createAccount(args[0],new String(password),true);System.out.println("Account saved. Restart the server to revoke existing sessions.");}
        finally{java.util.Arrays.fill(password,'\0');}
    }
}
