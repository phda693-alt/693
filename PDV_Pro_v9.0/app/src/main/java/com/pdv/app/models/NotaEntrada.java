package com.pdv.app.models;

/**
 * Modelo que representa uma Nota de Entrada (compra/recebimento de mercadorias).
 * Cada nota pode conter varios itens que alimentam o estoque dos produtos.
 */
public class NotaEntrada {
    private int id;
    private String numeroNota;
    private String fornecedor;
    private String dataEntrada;
    private double totalNota;
    private String observacao;
    private String status; // "confirmada", "cancelada", "pendente"
    private int usuarioId;
    private String usuarioNome;
    private int fornecedorId;
    private String condicaoPagamento; // ex: "30", "30/60", "a_vista", "personalizado"
    private String datasVencimento; // datas separadas por virgula

    public NotaEntrada() {
        this.status = "pendente";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNumeroNota() { return numeroNota; }
    public void setNumeroNota(String numeroNota) { this.numeroNota = numeroNota; }
    public String getFornecedor() { return fornecedor; }
    public void setFornecedor(String fornecedor) { this.fornecedor = fornecedor; }
    public String getDataEntrada() { return dataEntrada; }
    public void setDataEntrada(String dataEntrada) { this.dataEntrada = dataEntrada; }
    public double getTotalNota() { return totalNota; }
    public void setTotalNota(double totalNota) { this.totalNota = totalNota; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getUsuarioId() { return usuarioId; }
    public void setUsuarioId(int usuarioId) { this.usuarioId = usuarioId; }
    public String getUsuarioNome() { return usuarioNome; }
    public void setUsuarioNome(String usuarioNome) { this.usuarioNome = usuarioNome; }
    public int getFornecedorId() { return fornecedorId; }
    public void setFornecedorId(int fornecedorId) { this.fornecedorId = fornecedorId; }
    public String getCondicaoPagamento() { return condicaoPagamento; }
    public void setCondicaoPagamento(String condicaoPagamento) { this.condicaoPagamento = condicaoPagamento; }
    public String getDatasVencimento() { return datasVencimento; }
    public void setDatasVencimento(String datasVencimento) { this.datasVencimento = datasVencimento; }

    @Override
    public String toString() {
        return "Nota #" + (numeroNota != null ? numeroNota : String.valueOf(id));
    }
}
