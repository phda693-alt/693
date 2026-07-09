package com.pdv.app.models;

public class ValeDebito {
    private int id, caixaId, centroCustoId, usuarioId;
    private String descricao, data, centroCustoNome, usuarioNome;
    private double valor;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getCaixaId() { return caixaId; }
    public void setCaixaId(int c) { this.caixaId = c; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String d) { this.descricao = d; }
    public String getData() { return data; }
    public void setData(String d) { this.data = d; }
    public double getValor() { return valor; }
    public void setValor(double v) { this.valor = v; }
    public int getCentroCustoId() { return centroCustoId; }
    public void setCentroCustoId(int id) { this.centroCustoId = id; }
    public String getCentroCustoNome() { return centroCustoNome; }
    public void setCentroCustoNome(String nome) { this.centroCustoNome = nome; }
    public int getUsuarioId() { return usuarioId; }
    public void setUsuarioId(int id) { this.usuarioId = id; }
    public String getUsuarioNome() { return usuarioNome; }
    public void setUsuarioNome(String nome) { this.usuarioNome = nome; }
}
