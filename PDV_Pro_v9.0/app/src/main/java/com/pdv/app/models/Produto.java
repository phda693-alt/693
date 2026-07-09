package com.pdv.app.models;

public class Produto {
    private int id;
    private String codigo;
    private String descricao;
    private String unidade;
    private int tipoProdutoId;
    private String tipoProdutoDesc;
    private double precoCusto;
    private double precoVenda;
    private double estoque;
    private double estoqueMinimo;
    private String codigoBarras;
    private String fotoBase64;
    private boolean ativo;

    public Produto() { this.ativo = true; this.unidade = "UN"; this.codigoBarras = ""; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }
    public int getTipoProdutoId() { return tipoProdutoId; }
    public void setTipoProdutoId(int tipoProdutoId) { this.tipoProdutoId = tipoProdutoId; }
    public String getTipoProdutoDesc() { return tipoProdutoDesc; }
    public void setTipoProdutoDesc(String d) { this.tipoProdutoDesc = d; }
    public double getPrecoCusto() { return precoCusto; }
    public void setPrecoCusto(double precoCusto) { this.precoCusto = precoCusto; }
    public double getPrecoVenda() { return precoVenda; }
    public void setPrecoVenda(double precoVenda) { this.precoVenda = precoVenda; }
    public double getEstoque() { return estoque; }
    public void setEstoque(double estoque) { this.estoque = estoque; }
    public double getEstoqueMinimo() { return estoqueMinimo; }
    public void setEstoqueMinimo(double estoqueMinimo) { this.estoqueMinimo = estoqueMinimo; }
    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }
    public String getFotoBase64() { return fotoBase64; }
    public void setFotoBase64(String fotoBase64) { this.fotoBase64 = fotoBase64; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Override
    public String toString() { return descricao != null ? descricao : ""; }
}
