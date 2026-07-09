package com.pdv.app.models;

/**
 * Modelo que representa um item de uma Nota de Entrada.
 * Cada item referencia um produto e a quantidade recebida.
 */
public class ItemNotaEntrada {
    private int id;
    private int notaEntradaId;
    private int produtoId;
    private String descricaoProduto;
    private double quantidade;
    private double custoUnitario;
    private double total;

    public ItemNotaEntrada() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getNotaEntradaId() { return notaEntradaId; }
    public void setNotaEntradaId(int notaEntradaId) { this.notaEntradaId = notaEntradaId; }
    public int getProdutoId() { return produtoId; }
    public void setProdutoId(int produtoId) { this.produtoId = produtoId; }
    public String getDescricaoProduto() { return descricaoProduto; }
    public void setDescricaoProduto(String descricaoProduto) { this.descricaoProduto = descricaoProduto; }
    public double getQuantidade() { return quantidade; }
    public void setQuantidade(double quantidade) { this.quantidade = quantidade; }
    public double getCustoUnitario() { return custoUnitario; }
    public void setCustoUnitario(double custoUnitario) { this.custoUnitario = custoUnitario; }
    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    @Override
    public String toString() {
        return descricaoProduto != null ? descricaoProduto : "";
    }
}
