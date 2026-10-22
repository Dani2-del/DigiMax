package com.transcaribe.transcaribe.Util;


import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println(new BCryptPasswordEncoder().encode("1234"));
            return;
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        for (String password : args) {
            System.out.println(password + " => " + encoder.encode(password));
        }
    }
}
