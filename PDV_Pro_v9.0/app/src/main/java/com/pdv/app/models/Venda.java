package com.pdv.app.models;

public class Venda {
    private int id, clienteId, vendedorId, entregadorId, caixaId;
    private String dataVenda, clienteNome, vendedorNome, entregadorNome;
    private double totalBruto, descontoValor, acrescimoValor, totalLiquido, valorRecebido, troco;
    private String descontoTipo, acrescimoTipo, observacao, status;

    public Venda() { this.status = "finalizada"; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getClienteId() { return clienteId; }
    public void setClienteId(int c) { this.clienteId = c; }
    public int getVendedorId() { return vendedorId; }
    public void setVendedorId(int v) { this.vendedorId = v; }
    public int getEntregadorId() { return entregadorId; }
    public void setEntregadorId(int e) { this.entregadorId = e; }
    public int getCaixaId() { return caixaId; }
    public void setCaixaId(int c) { this.caixaId = c; }
    public String getDataVenda() { return dataVenda; }
    public void setDataVenda(String d) { this.dataVenda = d; }
    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String c) { this.clienteNome = c; }
    public String getVendedorNome() { return vendedorNome; }
    public void setVendedorNome(String v) { this.vendedorNome = v; }
    public String getEntregadorNome() { return entregadorNome; }
    public void setEntregadorNome(String e) { this.entregadorNome = e; }
    public double getTotalBruto() { return totalBruto; }
    public void setTotalBruto(double t) { this.totalBruto = t; }
    public double getDescontoValor() { return descontoValor; }
    public void setDescontoValor(double d) { this.descontoValor = d; }
    public double getAcrescimoValor() { return acrescimoValor; }
    public void setAcrescimoValor(double a) { this.acrescimoValor = a; }
    public double getTotalLiquido() { return totalLiquido; }
    public void setTotalLiquido(double t) { this.totalLiquido = t; }
    public double getValorRecebido() { return valorRecebido; }
    public void setValorRecebido(double v) { this.valorRecebido = v; }
    public double getTroco() { return troco; }
    public void setTroco(double t) { this.troco = t; }
    public String getDescontoTipo() { return descontoTipo; }
    public void setDescontoTipo(String d) { this.descontoTipo = d; }
    public String getAcrescimoTipo() { return acrescimoTipo; }
    public void setAcrescimoTipo(String a) { this.acrescimoTipo = a; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String o) { this.observacao = o; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
}
