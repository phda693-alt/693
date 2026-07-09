package com.pdv.app.models;

public class Empresa {
    private int id;
    private String razaoSocial, nomeFantasia, cnpj, ie, endereco, numero, bairro, cidade, uf, cep, telefone, email;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getRazaoSocial() { return razaoSocial; }
    public void setRazaoSocial(String r) { this.razaoSocial = r; }
    public String getNomeFantasia() { return nomeFantasia; }
    public void setNomeFantasia(String n) { this.nomeFantasia = n; }
    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }
    public String getIe() { return ie; }
    public void setIe(String ie) { this.ie = ie; }
    public String getEndereco() { return endereco; }
    public void setEndereco(String e) { this.endereco = e; }
    public String getNumero() { return numero; }
    public void setNumero(String n) { this.numero = n; }
    public String getBairro() { return bairro; }
    public void setBairro(String b) { this.bairro = b; }
    public String getCidade() { return cidade; }
    public void setCidade(String c) { this.cidade = c; }
    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }
    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String t) { this.telefone = t; }
    public String getEmail() { return email; }
    public void setEmail(String e) { this.email = e; }
}
