package com.pdv.app.models;

public class ObservacaoCupom {
    private int id;
    private String texto;
    private boolean ativo;

    public ObservacaoCupom() { this.ativo = true; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Override
    public String toString() { return texto != null ? texto : ""; }
}
