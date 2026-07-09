package com.pdv.app.models;

public class TipoProduto {
    private int id;
    private String descricao;
    private boolean ativo;

    public TipoProduto() { this.ativo = true; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String d) { this.descricao = d; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Override
    public String toString() { return descricao != null ? descricao : ""; }
}
