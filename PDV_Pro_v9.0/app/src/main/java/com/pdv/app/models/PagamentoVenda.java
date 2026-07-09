package com.pdv.app.models;

public class PagamentoVenda {
    private int id, vendaId, formaPagamentoId, parcelas;
    private String formaDescricao, bandeira;
    private double valor;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getVendaId() { return vendaId; }
    public void setVendaId(int v) { this.vendaId = v; }
    public int getFormaPagamentoId() { return formaPagamentoId; }
    public void setFormaPagamentoId(int f) { this.formaPagamentoId = f; }
    public int getParcelas() { return parcelas; }
    public void setParcelas(int p) { this.parcelas = p; }
    public String getFormaDescricao() { return formaDescricao; }
    public void setFormaDescricao(String f) { this.formaDescricao = f; }
    public String getBandeira() { return bandeira; }
    public void setBandeira(String b) { this.bandeira = b; }
    public double getValor() { return valor; }
    public void setValor(double v) { this.valor = v; }
}
