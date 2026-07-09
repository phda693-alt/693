package com.pdv.app.models;

import java.util.Objects;

/**
 * Modelo de Armario para Sauna.
 * Representa um armario fisico que pode ser utilizado por clientes da sauna.
 * 
 * Status possiveis:
 * - livre: armario disponivel para uso
 * - ocupado: armario em uso por um cliente (chave entregue)
 * - manutencao: armario em manutencao, indisponivel
 * 
 * v6.9.5 - Modulo de Armarios para Sauna
 * v7.0.0 - Adicionado equals/hashCode para suporte a DiffUtil e StableIds
 */
public class ArmarioSauna {
    private int id;
    private int numero;
    private String descricao;
    private String localizacao; // ex: "Ala A", "Ala B", "Andar 1"
    private boolean ativo;

    public ArmarioSauna() {}

    public ArmarioSauna(int id, int numero, String descricao, String localizacao, boolean ativo) {
        this.id = id;
        this.numero = numero;
        this.descricao = descricao;
        this.localizacao = localizacao;
        this.ativo = ativo;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getLocalizacao() { return localizacao; }
    public void setLocalizacao(String localizacao) { this.localizacao = localizacao; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Override
    public String toString() {
        return "Armario " + numero;
    }

    /**
     * v7.0.0 - equals baseado no ID para suporte a DiffUtil.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArmarioSauna that = (ArmarioSauna) o;
        return id == that.id && numero == that.numero && ativo == that.ativo
                && Objects.equals(descricao, that.descricao)
                && Objects.equals(localizacao, that.localizacao);
    }

    /**
     * v7.0.0 - hashCode baseado no ID para suporte a DiffUtil.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, numero, descricao, localizacao, ativo);
    }
}
