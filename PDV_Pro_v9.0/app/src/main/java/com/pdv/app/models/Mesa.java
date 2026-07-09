package com.pdv.app.models;

/**
 * Modelo de Mesa para o sistema de mesas.
 */
public class Mesa {
    private int id;
    private int numero;
    private String descricao;
    private int capacidade;
    private boolean ativa;

    public Mesa() {}

    public Mesa(int id, int numero, String descricao, int capacidade, boolean ativa) {
        this.id = id;
        this.numero = numero;
        this.descricao = descricao;
        this.capacidade = capacidade;
        this.ativa = ativa;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public int getCapacidade() { return capacidade; }
    public void setCapacidade(int capacidade) { this.capacidade = capacidade; }

    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }

    @Override
    public String toString() {
        return "Mesa " + numero;
    }
}
