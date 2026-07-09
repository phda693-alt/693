package com.pdv.app.models;

/**
 * Modelo para registrar recebimentos (baixas) de Contas a Receber.
 * Permite pagamentos parciais ou totais de uma conta pendente.
 */
public class RecebimentoConta {
    private int id, contaReceberId;
    private double valor;
    private String dataRecebimento, formaPagamento, observacao;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getContaReceberId() { return contaReceberId; }
    public void setContaReceberId(int contaReceberId) { this.contaReceberId = contaReceberId; }

    public double getValor() { return valor; }
    public void setValor(double valor) { this.valor = valor; }

    public String getDataRecebimento() { return dataRecebimento; }
    public void setDataRecebimento(String dataRecebimento) { this.dataRecebimento = dataRecebimento; }

    public String getFormaPagamento() { return formaPagamento; }
    public void setFormaPagamento(String formaPagamento) { this.formaPagamento = formaPagamento; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
