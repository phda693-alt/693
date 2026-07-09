package com.pdv.app.models;

public class Caixa {
    private int id, usuarioId;
    private String dataAbertura, dataFechamento, status, observacao, usuarioNome;
    private double valorAbertura, valorFechamento;

    public Caixa() { this.status = "aberto"; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUsuarioId() { return usuarioId; }
    public void setUsuarioId(int u) { this.usuarioId = u; }
    public String getDataAbertura() { return dataAbertura; }
    public void setDataAbertura(String d) { this.dataAbertura = d; }
    public String getDataFechamento() { return dataFechamento; }
    public void setDataFechamento(String d) { this.dataFechamento = d; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String o) { this.observacao = o; }
    public String getUsuarioNome() { return usuarioNome; }
    public void setUsuarioNome(String u) { this.usuarioNome = u; }
    public double getValorAbertura() { return valorAbertura; }
    public void setValorAbertura(double v) { this.valorAbertura = v; }
    public double getValorFechamento() { return valorFechamento; }
    public void setValorFechamento(double v) { this.valorFechamento = v; }
}
