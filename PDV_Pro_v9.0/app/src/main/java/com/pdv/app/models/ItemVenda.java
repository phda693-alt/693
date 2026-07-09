package com.pdv.app.models;

import java.util.ArrayList;
import java.util.List;

public class ItemVenda {
    private int id, vendaId, produtoId;
    private String descricaoProduto, fotoBase64;
    private double quantidade, precoUnitario, desconto, total;

    // v6.7.5 - Adicionais selecionados no ato da venda
    private List<AdicionalSelecionado> adicionais = new ArrayList<>();

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getVendaId() { return vendaId; }
    public void setVendaId(int v) { this.vendaId = v; }
    public int getProdutoId() { return produtoId; }
    public void setProdutoId(int p) { this.produtoId = p; }
    public String getDescricaoProduto() { return descricaoProduto; }
    public void setDescricaoProduto(String d) { this.descricaoProduto = d; }
    public String getFotoBase64() { return fotoBase64; }
    public void setFotoBase64(String f) { this.fotoBase64 = f; }
    public double getQuantidade() { return quantidade; }
    public void setQuantidade(double q) { this.quantidade = q; }
    public double getPrecoUnitario() { return precoUnitario; }
    public void setPrecoUnitario(double p) { this.precoUnitario = p; }
    public double getDesconto() { return desconto; }
    public void setDesconto(double d) { this.desconto = d; }
    public double getTotal() { return total; }
    public void setTotal(double t) { this.total = t; }

    // v6.7.5 - Metodos para adicionais
    public List<AdicionalSelecionado> getAdicionais() { return adicionais; }
    public void setAdicionais(List<AdicionalSelecionado> adicionais) { this.adicionais = adicionais; }

    /**
     * Retorna o total dos adicionais selecionados (preco unitario de cada adicional * quantidade do item).
     */
    public double getTotalAdicionais() {
        double totalAd = 0;
        if (adicionais != null) {
            for (AdicionalSelecionado ad : adicionais) {
                totalAd += ad.getPreco();
            }
        }
        return totalAd * quantidade;
    }

    /**
     * Retorna o total do item incluindo adicionais.
     */
    public double getTotalComAdicionais() {
        return total + getTotalAdicionais();
    }

    /**
     * Retorna a descricao dos adicionais formatada para exibicao.
     */
    public String getAdicionaisDescricao() {
        if (adicionais == null || adicionais.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (AdicionalSelecionado ad : adicionais) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ad.getDescricao());
            if (ad.getPreco() > 0) {
                sb.append(" (+R$ ").append(String.format("%.2f", ad.getPreco())).append(")");
            }
        }
        return sb.toString();
    }

    /**
     * Classe interna para representar um adicional selecionado no ato da venda.
     */
    public static class AdicionalSelecionado {
        private int adicionalId;
        private String descricao;
        private double preco;

        public AdicionalSelecionado() {}

        public AdicionalSelecionado(int adicionalId, String descricao, double preco) {
            this.adicionalId = adicionalId;
            this.descricao = descricao;
            this.preco = preco;
        }

        public int getAdicionalId() { return adicionalId; }
        public void setAdicionalId(int adicionalId) { this.adicionalId = adicionalId; }
        public String getDescricao() { return descricao; }
        public void setDescricao(String descricao) { this.descricao = descricao; }
        public double getPreco() { return preco; }
        public void setPreco(double preco) { this.preco = preco; }
    }
}
