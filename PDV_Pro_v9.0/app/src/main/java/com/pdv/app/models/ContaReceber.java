package com.pdv.app.models;

/**
 * Modelo para Contas a Receber.
 * Cada registro representa um valor pendente de recebimento vinculado a um cliente.
 * Quando uma venda e feita com forma de pagamento "Contas a Receber",
 * o valor e acumulado na conta do cliente para cobranca posterior.
 */
public class ContaReceber {
    private int id, clienteId, vendaId;
    private String clienteNome, dataVenda, dataVencimento, dataPagamento;
    private double valorOriginal, valorPago, valorPendente;
    private String status; // pendente, pago_parcial, pago, cancelado
    private String observacao;

    public ContaReceber() {
        this.status = "pendente";
        this.valorPago = 0;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClienteId() { return clienteId; }
    public void setClienteId(int clienteId) { this.clienteId = clienteId; }

    public int getVendaId() { return vendaId; }
    public void setVendaId(int vendaId) { this.vendaId = vendaId; }

    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }

    public String getDataVenda() { return dataVenda; }
    public void setDataVenda(String dataVenda) { this.dataVenda = dataVenda; }

    public String getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(String dataVencimento) { this.dataVencimento = dataVencimento; }

    public String getDataPagamento() { return dataPagamento; }
    public void setDataPagamento(String dataPagamento) { this.dataPagamento = dataPagamento; }

    public double getValorOriginal() { return valorOriginal; }
    public void setValorOriginal(double valorOriginal) { this.valorOriginal = valorOriginal; }

    public double getValorPago() { return valorPago; }
    public void setValorPago(double valorPago) { this.valorPago = valorPago; }

    public double getValorPendente() { return valorPendente; }
    public void setValorPendente(double valorPendente) { this.valorPendente = valorPendente; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    @Override
    public String toString() {
        return "Venda #" + vendaId + " - R$ " + String.format("%.2f", valorOriginal);
    }
}
