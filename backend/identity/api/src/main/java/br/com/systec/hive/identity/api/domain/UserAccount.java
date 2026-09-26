package br.com.systec.hive.identity.api.domain;

import java.io.Serial;
import java.io.Serializable;

public class UserAccount implements Serializable {
    @Serial
    private static final long serialVersionUID = -2512432484959672840L;

    private String name;
    private String username;
    private String password;
    private String email;
    private String document;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }
}
