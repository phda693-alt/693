package com.pdv.app.models;

/**
 * Modelo de Garcom para o sistema de mesas.
 */
public class Garcom {
    private int id;
    private String nome;
    private String celular;
    private boolean ativo;

    public Garcom() {}

    public Garcom(int id, String nome, String celular, boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.celular = celular;
        this.ativo = ativo;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Override
    public String toString() {
        return nome;
    }
}
