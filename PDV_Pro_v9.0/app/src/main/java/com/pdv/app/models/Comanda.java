package com.pdv.app.models;

import java.util.ArrayList;
import java.util.List;

public class Comanda {
    private int id;
    private int numero;
    private int clienteId;
    private int vendedorId;
    private int caixaId;
    private String clienteNome;
    private String vendedorNome;
    private String dataAbertura;
    private String dataFechamento;
    private double totalItens;
    private String observacao;
    private String status; // aberta, fechada, cancelada
    private List<ItemComanda> itens;

    public Comanda() {
        this.status = "aberta";
        this.itens = new ArrayList<>();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public int getClienteId() { return clienteId; }
    public void setClienteId(int clienteId) { this.clienteId = clienteId; }
    public int getVendedorId() { return vendedorId; }
    public void setVendedorId(int vendedorId) { this.vendedorId = vendedorId; }
    public int getCaixaId() { return caixaId; }
    public void setCaixaId(int caixaId) { this.caixaId = caixaId; }
    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }
    public String getVendedorNome() { return vendedorNome; }
    public void setVendedorNome(String vendedorNome) { this.vendedorNome = vendedorNome; }
    public String getDataAbertura() { return dataAbertura; }
    public void setDataAbertura(String dataAbertura) { this.dataAbertura = dataAbertura; }
    public String getDataFechamento() { return dataFechamento; }
    public void setDataFechamento(String dataFechamento) { this.dataFechamento = dataFechamento; }
    public double getTotalItens() { return totalItens; }
    public void setTotalItens(double totalItens) { this.totalItens = totalItens; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<ItemComanda> getItens() { return itens; }
    public void setItens(List<ItemComanda> itens) { this.itens = itens; }
}
