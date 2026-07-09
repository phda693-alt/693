package com.pdv.app.models;

public class Vendedor {
    private int id;
    private String nome, celular;
    private double comissao;
    private boolean ativo;

    public Vendedor() { this.ativo = true; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }
    public double getComissao() { return comissao; }
    public void setComissao(double comissao) { this.comissao = comissao; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Override
    public String toString() { return nome != null ? nome : ""; }
}
