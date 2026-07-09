package com.pdv.app.permissions;

import com.pdv.app.models.Permissao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe central que define TODAS as permissoes do sistema PDV Pro.
 * 
 * v6.0.0 - Sistema de Permissoes Avancado:
 *   - Permissoes granulares por botao/componente/funcao
 *   - Suporte a permissoes personalizadas por usuario (override de perfil)
 *   - Controle de visibilidade E habilitacao independentes
 *   - Permissoes de botoes do dashboard (tela principal)
 *   - Condicao de caixa aberto para vendas
 * 
 * Estrutura hierarquica:
 *   MODULO > ACAO > CHAVE UNICA
 * 
 * Convencao de chaves: "modulo.acao"
 * 
 * Esta classe e a UNICA fonte de verdade para as permissoes do sistema.
 */
public final class PermissionConstants {

    private PermissionConstants() {} // Nao instanciavel

    // =========================================================================
    // MODULO: DASHBOARD (Botoes da Tela Principal)
    // Controle granular de cada botao do menu principal
    // =========================================================================
    public static final String DASHBOARD_BTN_VENDAS            = "dashboard.btn_vendas";
    public static final String DASHBOARD_BTN_COMANDAS          = "dashboard.btn_comandas";
    public static final String DASHBOARD_BTN_PRODUTOS          = "dashboard.btn_produtos";
    public static final String DASHBOARD_BTN_GERENCIAR_PRODUTOS = "dashboard.btn_gerenciar_produtos";
    public static final String DASHBOARD_BTN_CLIENTES          = "dashboard.btn_clientes";
    public static final String DASHBOARD_BTN_CAIXA             = "dashboard.btn_caixa";
    public static final String DASHBOARD_BTN_RELATORIOS        = "dashboard.btn_relatorios";
    public static final String DASHBOARD_BTN_HISTORICO         = "dashboard.btn_historico";
    public static final String DASHBOARD_BTN_EMPRESA           = "dashboard.btn_empresa";
    public static final String DASHBOARD_BTN_VENDEDORES        = "dashboard.btn_vendedores";
    public static final String DASHBOARD_BTN_ENTREGADORES      = "dashboard.btn_entregadores";
    public static final String DASHBOARD_BTN_USUARIOS          = "dashboard.btn_usuarios";
    public static final String DASHBOARD_BTN_PERFIS            = "dashboard.btn_perfis";
    public static final String DASHBOARD_BTN_FORMAS_PAGAMENTO  = "dashboard.btn_formas_pagamento";
    public static final String DASHBOARD_BTN_TIPOS_PRODUTO     = "dashboard.btn_tipos_produto";
    public static final String DASHBOARD_BTN_OBSERVACOES       = "dashboard.btn_observacoes";
    public static final String DASHBOARD_BTN_IMPRESSORA        = "dashboard.btn_impressora";
    public static final String DASHBOARD_BTN_BACKUP            = "dashboard.btn_backup";
    public static final String DASHBOARD_BTN_LICENCA           = "dashboard.btn_licenca";
    public static final String DASHBOARD_BTN_TROCAR_SENHA      = "dashboard.btn_trocar_senha";
    public static final String DASHBOARD_BTN_MODO_ENTREGADOR   = "dashboard.btn_modo_entregador";
    public static final String DASHBOARD_BTN_GERENCIAR_ENTREGAS = "dashboard.btn_gerenciar_entregas";
    public static final String DASHBOARD_BTN_CONTAS_RECEBER    = "dashboard.btn_contas_receber";
    public static final String DASHBOARD_BTN_WHATSBOT          = "dashboard.btn_whatsbot";
    public static final String DASHBOARD_BTN_ENTRADA_NOTAS     = "dashboard.btn_entrada_notas";
    public static final String DASHBOARD_BTN_TAXA_ENTREGA      = "dashboard.btn_taxa_entrega";
    public static final String DASHBOARD_BTN_SOBRE             = "dashboard.btn_sobre";
    public static final String DASHBOARD_BTN_PAINEL_CHAMADOS   = "dashboard.btn_painel_chamados";
    public static final String DASHBOARD_BTN_GERENCIADOR_CHAMADOS = "dashboard.btn_gerenciador_chamados";
    public static final String DASHBOARD_BTN_PAINEL_COZINHA       = "dashboard.btn_painel_cozinha";
    public static final String DASHBOARD_BTN_WEB_COZINHA          = "dashboard.btn_web_cozinha";
    public static final String DASHBOARD_BTN_PAINEL_SENHAS        = "dashboard.btn_painel_senhas";
    public static final String DASHBOARD_BTN_ESTACIONAMENTO       = "dashboard.btn_estacionamento";
    public static final String DASHBOARD_BTN_MULTIIMPRESSORAS     = "dashboard.btn_multiimpressoras";
    public static final String DASHBOARD_BTN_CARDAPIO_QRCODE      = "dashboard.btn_cardapio_qrcode";
    public static final String DASHBOARD_BTN_DIAGNOSTICO          = "dashboard.btn_diagnostico";
    public static final String DASHBOARD_BTN_SERVIDOR_MYSQL       = "dashboard.btn_servidor_mysql";
    public static final String DASHBOARD_BTN_CRIAR_BANCO_MYSQL    = "dashboard.btn_criar_banco_mysql";
    public static final String DASHBOARD_BTN_USUARIOS_MYSQL       = "dashboard.btn_usuarios_mysql";
    public static final String DASHBOARD_BTN_MYSQL_ESPELHO        = "dashboard.btn_mysql_espelho";
    public static final String DASHBOARD_BTN_AGENDA               = "dashboard.btn_agenda";
    public static final String DASHBOARD_BTN_CONFIGURACOES        = "dashboard.btn_configuracoes";
    public static final String DASHBOARD_BTN_FORNECEDORES         = "dashboard.btn_fornecedores";

    // =========================================================================
    // MODULO: PAINEL DA COZINHA
    // =========================================================================
    public static final String PAINEL_COZINHA_ACESSAR  = "painel_cozinha.acessar";
    public static final String PAINEL_COZINHA_WEB      = "painel_cozinha.web";

    // =========================================================================
    // MODULO: PAINEL DE SENHAS
    // =========================================================================
    public static final String PAINEL_SENHAS_ACESSAR    = "painel_senhas.acessar";
    public static final String PAINEL_SENHAS_CHAMAR     = "painel_senhas.chamar";
    public static final String PAINEL_SENHAS_LIMPAR     = "painel_senhas.limpar";
    public static final String PAINEL_SENHAS_TESTAR_SOM = "painel_senhas.testar_som";
    public static final String PAINEL_SENHAS_WEB        = "painel_senhas.web";

    // =========================================================================
    // MODULO: ESTACIONAMENTO
    // =========================================================================
    public static final String ESTACIONAMENTO_ACESSAR           = "estacionamento.acessar";
    public static final String ESTACIONAMENTO_ENTRADA           = "estacionamento.entrada";
    public static final String ESTACIONAMENTO_FINALIZAR_SAIDA   = "estacionamento.finalizar_saida";
    public static final String ESTACIONAMENTO_CANCELAR          = "estacionamento.cancelar";
    public static final String ESTACIONAMENTO_HISTORICO         = "estacionamento.historico";
    public static final String ESTACIONAMENTO_IMPRIMIR_CHEGADA  = "estacionamento.imprimir_chegada";
    public static final String ESTACIONAMENTO_IMPRIMIR_ENTREGA  = "estacionamento.imprimir_entrega";
    public static final String ESTACIONAMENTO_LEITOR_PLACA      = "estacionamento.leitor_placa";
    public static final String ESTACIONAMENTO_CONFIGURAR_VALOR  = "estacionamento.configurar_valor";

    // =========================================================================
    // MODULO: MULTIIMPRESSORAS
    // =========================================================================
    public static final String MULTIIMPRESSORAS_ACESSAR          = "multiimpressoras.acessar";
    public static final String MULTIIMPRESSORAS_ATIVAR           = "multiimpressoras.ativar";
    public static final String MULTIIMPRESSORAS_CRIAR_REGRA      = "multiimpressoras.criar_regra";
    public static final String MULTIIMPRESSORAS_EDITAR_REGRA     = "multiimpressoras.editar_regra";
    public static final String MULTIIMPRESSORAS_EXCLUIR_REGRA    = "multiimpressoras.excluir_regra";
    public static final String MULTIIMPRESSORAS_TESTAR_IMPRESSORA = "multiimpressoras.testar_impressora";
    public static final String MULTIIMPRESSORAS_CONFIGURAR_DRIVER = "multiimpressoras.configurar_driver";

    // =========================================================================
    // MODULO: VENDAS
    // =========================================================================
    public static final String VENDAS_ACESSAR           = "vendas.acessar";
    public static final String VENDAS_CRIAR             = "vendas.criar";
    public static final String VENDAS_APLICAR_DESCONTO  = "vendas.aplicar_desconto";
    public static final String VENDAS_APLICAR_ACRESCIMO = "vendas.aplicar_acrescimo";
    public static final String VENDAS_ESCOLHER_CLIENTE  = "vendas.escolher_cliente";
    public static final String VENDAS_ESCOLHER_VENDEDOR = "vendas.escolher_vendedor";
    public static final String VENDAS_ESCOLHER_ENTREGADOR = "vendas.escolher_entregador";
    public static final String VENDAS_LEITOR_CODIGO_BARRAS = "vendas.leitor_codigo_barras";
    public static final String VENDAS_IMPRIMIR_CANHOTO_SENHA = "vendas.imprimir_canhoto_senha";
    public static final String VENDAS_IMPRIMIR_DUAS_VIAS = "vendas.imprimir_duas_vias";
    public static final String VENDAS_EXIBIR_SENHA_CUPOM = "vendas.exibir_senha_cupom";

    // =========================================================================
    // MODULO: HISTORICO DE VENDAS
    // =========================================================================
    public static final String HISTORICO_ACESSAR        = "historico.acessar";
    public static final String HISTORICO_CANCELAR_VENDA = "historico.cancelar_venda";
    public static final String HISTORICO_DEVOLVER_VENDA = "historico.devolver_venda";
    public static final String HISTORICO_REIMPRIMIR     = "historico.reimprimir";
    public static final String HISTORICO_ENVIAR_WHATSAPP = "historico.enviar_whatsapp";

    // =========================================================================
    // MODULO: COMANDAS
    // =========================================================================
    public static final String COMANDAS_ACESSAR         = "comandas.acessar";
    public static final String COMANDAS_CRIAR           = "comandas.criar";
    public static final String COMANDAS_EDITAR          = "comandas.editar";
    public static final String COMANDAS_FECHAR          = "comandas.fechar";
    public static final String COMANDAS_CANCELAR        = "comandas.cancelar";
    public static final String COMANDAS_IMPRIMIR        = "comandas.imprimir";
    public static final String COMANDAS_LEITOR_CODIGO_BARRAS = "comandas.leitor_codigo_barras";

    // =========================================================================
    // MODULO: CAIXA
    // =========================================================================
    public static final String CAIXA_ACESSAR            = "caixa.acessar";
    public static final String CAIXA_ABRIR              = "caixa.abrir";
    public static final String CAIXA_FECHAR             = "caixa.fechar";
    public static final String CAIXA_VALE_DEBITO        = "caixa.vale_debito";
    public static final String CAIXA_REIMPRIMIR_FECHAMENTO = "caixa.reimprimir_fechamento";

    // =========================================================================
    // MODULO: PRODUTOS
    // =========================================================================
    public static final String PRODUTOS_ACESSAR         = "produtos.acessar";
    public static final String PRODUTOS_CRIAR           = "produtos.criar";
    public static final String PRODUTOS_EDITAR          = "produtos.editar";
    public static final String PRODUTOS_EXCLUIR         = "produtos.excluir";

    // =========================================================================
    // MODULO: GERENCIAR PRODUTOS
    // =========================================================================
    public static final String GERENCIAR_PRODUTOS_ACESSAR  = "gerenciar_produtos.acessar";
    public static final String GERENCIAR_PRODUTOS_EDITAR   = "gerenciar_produtos.editar";
    public static final String GERENCIAR_PRODUTOS_INATIVAR = "gerenciar_produtos.inativar";

    // =========================================================================
    // MODULO: CLIENTES
    // =========================================================================
    public static final String CLIENTES_ACESSAR         = "clientes.acessar";
    public static final String CLIENTES_CRIAR           = "clientes.criar";
    public static final String CLIENTES_EDITAR          = "clientes.editar";
    public static final String CLIENTES_EXCLUIR         = "clientes.excluir";

    // =========================================================================
    // MODULO: VENDEDORES
    // =========================================================================
    public static final String VENDEDORES_ACESSAR       = "vendedores.acessar";
    public static final String VENDEDORES_CRIAR         = "vendedores.criar";
    public static final String VENDEDORES_EDITAR        = "vendedores.editar";
    public static final String VENDEDORES_EXCLUIR       = "vendedores.excluir";

    // =========================================================================
    // MODULO: ENTREGADORES
    // =========================================================================
    public static final String ENTREGADORES_ACESSAR     = "entregadores.acessar";
    public static final String ENTREGADORES_CRIAR       = "entregadores.criar";
    public static final String ENTREGADORES_EDITAR      = "entregadores.editar";
    public static final String ENTREGADORES_EXCLUIR     = "entregadores.excluir";

    // =========================================================================
    // MODULO: EMPRESA
    // =========================================================================
    public static final String EMPRESA_ACESSAR          = "empresa.acessar";
    public static final String EMPRESA_EDITAR           = "empresa.editar";

    // =========================================================================
    // MODULO: USUARIOS
    // =========================================================================
    public static final String USUARIOS_ACESSAR         = "usuarios.acessar";
    public static final String USUARIOS_CRIAR           = "usuarios.criar";
    public static final String USUARIOS_EDITAR          = "usuarios.editar";
    public static final String USUARIOS_EXCLUIR         = "usuarios.excluir";

    // =========================================================================
    // MODULO: PERFIS E PERMISSOES
    // =========================================================================
    public static final String PERFIS_ACESSAR           = "perfis.acessar";
    public static final String PERFIS_CRIAR             = "perfis.criar";
    public static final String PERFIS_EDITAR            = "perfis.editar";
    public static final String PERFIS_EXCLUIR           = "perfis.excluir";
    public static final String PERFIS_GERENCIAR_PERMISSOES = "perfis.gerenciar_permissoes";
    public static final String PERFIS_PERMISSOES_USUARIO = "perfis.permissoes_usuario";

    // =========================================================================
    // MODULO: FORMAS DE PAGAMENTO
    // =========================================================================
    public static final String FORMAS_PAGAMENTO_ACESSAR = "formas_pagamento.acessar";
    public static final String FORMAS_PAGAMENTO_CRIAR   = "formas_pagamento.criar";
    public static final String FORMAS_PAGAMENTO_EDITAR  = "formas_pagamento.editar";
    public static final String FORMAS_PAGAMENTO_EXCLUIR = "formas_pagamento.excluir";

    // =========================================================================
    // MODULO: TIPOS DE PRODUTO
    // =========================================================================
    public static final String TIPOS_PRODUTO_ACESSAR    = "tipos_produto.acessar";
    public static final String TIPOS_PRODUTO_CRIAR      = "tipos_produto.criar";
    public static final String TIPOS_PRODUTO_EDITAR     = "tipos_produto.editar";
    public static final String TIPOS_PRODUTO_EXCLUIR    = "tipos_produto.excluir";

    // =========================================================================
    // MODULO: ADICIONAIS
    // =========================================================================
    public static final String ADICIONAIS_ACESSAR       = "adicionais.acessar";
    public static final String ADICIONAIS_CRIAR         = "adicionais.criar";
    public static final String ADICIONAIS_EDITAR        = "adicionais.editar";
    public static final String ADICIONAIS_EXCLUIR       = "adicionais.excluir";

    // =========================================================================
    // MODULO: DASHBOARD - Botao Adicionais
    // =========================================================================
    public static final String DASHBOARD_BTN_ADICIONAIS = "dashboard.btn_adicionais";

    // =========================================================================
    // MODULO: OBSERVACOES DO CUPOM
    // =========================================================================
    public static final String OBSERVACOES_ACESSAR      = "observacoes.acessar";
    public static final String OBSERVACOES_CRIAR        = "observacoes.criar";
    public static final String OBSERVACOES_EDITAR       = "observacoes.editar";
    public static final String OBSERVACOES_EXCLUIR      = "observacoes.excluir";

    // =========================================================================
    // MODULO: RELATORIOS
    // =========================================================================
    public static final String RELATORIOS_ACESSAR       = "relatorios.acessar";
    public static final String RELATORIOS_VENDAS        = "relatorios.vendas";
    public static final String RELATORIOS_LUCRATIVIDADE = "relatorios.lucratividade";
    public static final String RELATORIOS_VENDEDOR      = "relatorios.vendedor";
    public static final String RELATORIOS_ENTREGADOR    = "relatorios.entregador";
    public static final String RELATORIOS_CLIENTE       = "relatorios.cliente";
    public static final String RELATORIOS_PRODUTOS      = "relatorios.produtos";
    public static final String RELATORIOS_CAIXA         = "relatorios.caixa";
    public static final String RELATORIOS_GARCOM        = "relatorios.garcom_porcentagem";
    public static final String RELATORIOS_TAXAS_ENTREGADOR = "relatorios.taxas_entregador";
    public static final String RELATORIOS_VALES_CAIXA   = "relatorios.vales_caixa";
    public static final String RELATORIOS_AUDITORIA     = "relatorios.auditoria_usuarios";

    // =========================================================================
    // MODULOS TECNICOS, AGENDA E CARDAPIO DIGITAL
    // =========================================================================
    public static final String DIAGNOSTICO_ACESSAR      = "diagnostico.acessar";
    public static final String SERVIDOR_MYSQL_ACESSAR   = "servidor_mysql.acessar";
    public static final String CRIAR_BANCO_MYSQL        = "servidor_mysql.criar_banco";
    public static final String USUARIOS_MYSQL_ACESSAR   = "servidor_mysql.gerenciar_usuarios";
    public static final String MYSQL_ESPELHO_ACESSAR    = "mysql_espelho.acessar";
    public static final String MYSQL_ESPELHO_SINCRONIZAR = "mysql_espelho.sincronizar";
    public static final String AGENDA_ACESSAR           = "agenda.acessar";
    public static final String AGENDA_CRIAR             = "agenda.criar";
    public static final String AGENDA_EDITAR            = "agenda.editar";
    public static final String AGENDA_CANCELAR          = "agenda.cancelar";
    public static final String CARDAPIO_QRCODE_ACESSAR  = "cardapio_qrcode.acessar";

    // =========================================================================
    // MODULO: CONFIGURACOES
    // =========================================================================
    public static final String CONFIG_IMPRESSORA_ACESSAR = "config_impressora.acessar";
    public static final String CONFIG_IMPRESSORA_EDITAR  = "config_impressora.editar";
    public static final String CONFIG_IMPRESSORA_DRIVER  = "config_impressora.driver";

    // =========================================================================
    // MODULO: BACKUP
    // =========================================================================
    public static final String BACKUP_ACESSAR           = "backup.acessar";
    public static final String BACKUP_REALIZAR          = "backup.realizar";
    public static final String BACKUP_RESTAURAR         = "backup.restaurar";
    public static final String BACKUP_CONFIGURAR        = "backup.configurar";

    // =========================================================================
    // MODULO: LICENCA
    // =========================================================================
    public static final String LICENCA_ACESSAR          = "licenca.acessar";
    public static final String LICENCA_ATIVAR           = "licenca.ativar";

    // =========================================================================
    // MODULO: WHATSAPP BOT
    // =========================================================================
    public static final String WHATSBOT_ACESSAR         = "whatsbot.acessar";
    public static final String WHATSBOT_CONFIGURAR      = "whatsbot.configurar";

    // =========================================================================
    // MODULO: ENTREGAS
    // =========================================================================
    public static final String ENTREGAS_ACESSAR         = "entregas.acessar";
    public static final String ENTREGAS_GERENCIAR       = "entregas.gerenciar";
    public static final String MODO_ENTREGADOR_ACESSAR  = "modo_entregador.acessar";

    // =========================================================================
    // MODULO: CONTAS A RECEBER
    // =========================================================================
    public static final String CONTAS_RECEBER_ACESSAR   = "contas_receber.acessar";
    public static final String CONTAS_RECEBER_RECEBER   = "contas_receber.receber";
    public static final String CONTAS_RECEBER_CANCELAR  = "contas_receber.cancelar";
    public static final String CONTAS_RECEBER_RELATORIO = "contas_receber.relatorio";

    // =========================================================================
    // MODULO: ENTRADA DE NOTAS
    // =========================================================================
    public static final String ENTRADA_NOTAS_ACESSAR    = "entrada_notas.acessar";
    public static final String ENTRADA_NOTAS_CRIAR      = "entrada_notas.criar";
    public static final String ENTRADA_NOTAS_CONFIRMAR  = "entrada_notas.confirmar";
    public static final String ENTRADA_NOTAS_CANCELAR   = "entrada_notas.cancelar";

    // =========================================================================
    // MODULO: FORNECEDORES
    // =========================================================================
    public static final String FORNECEDORES_ACESSAR     = "fornecedores.acessar";
    public static final String FORNECEDORES_CRIAR       = "fornecedores.criar";
    public static final String FORNECEDORES_EDITAR      = "fornecedores.editar";
    public static final String FORNECEDORES_EXCLUIR     = "fornecedores.excluir";

    // =========================================================================
    // MODULO: CONTAS A PAGAR
    // =========================================================================
    public static final String CONTAS_PAGAR_ACESSAR   = "contas_pagar.acessar";
    public static final String CONTAS_PAGAR_CRIAR     = "contas_pagar.criar";
    public static final String CONTAS_PAGAR_EDITAR    = "contas_pagar.editar";
    public static final String CONTAS_PAGAR_PAGAR     = "contas_pagar.pagar";
    public static final String CONTAS_PAGAR_CANCELAR  = "contas_pagar.cancelar";
    public static final String CONTAS_PAGAR_RELATORIO = "contas_pagar.relatorio";

    // =========================================================================
    // MODULO: CAIXAS NOMINAIS
    // =========================================================================
    public static final String CAIXAS_NOMINAIS_ACESSAR = "caixas_nominais.acessar";
    public static final String CAIXAS_NOMINAIS_CRIAR   = "caixas_nominais.criar";
    public static final String CAIXAS_NOMINAIS_EDITAR  = "caixas_nominais.editar";
    public static final String CAIXAS_NOMINAIS_EXCLUIR = "caixas_nominais.excluir";

    // =========================================================================
    // MODULO: TURNOS
    // =========================================================================
    public static final String TURNOS_ACESSAR = "turnos.acessar";
    public static final String TURNOS_CRIAR   = "turnos.criar";
    public static final String TURNOS_EDITAR  = "turnos.editar";
    public static final String TURNOS_EXCLUIR = "turnos.excluir";

    // =========================================================================
    // MODULO: VINCULOS USUARIO-CAIXA-TURNO
    // =========================================================================
    public static final String VINCULOS_ACESSAR  = "vinculos.acessar";
    public static final String VINCULOS_CRIAR    = "vinculos.criar";
    public static final String VINCULOS_EXCLUIR  = "vinculos.excluir";
    public static final String VINCULOS_RELATORIO = "vinculos.relatorio";

    // Dashboard buttons para novos modulos
    public static final String DASHBOARD_BTN_CONTAS_PAGAR    = "dashboard.btn_contas_pagar";
    public static final String DASHBOARD_BTN_CAIXAS_NOMINAIS = "dashboard.btn_caixas_nominais";
    public static final String DASHBOARD_BTN_TURNOS          = "dashboard.btn_turnos";
    public static final String DASHBOARD_BTN_VINCULOS        = "dashboard.btn_vinculos";

    // =========================================================================
    // MODULO: CONFIGURACOES GERAIS
    // =========================================================================
    public static final String CONFIG_GERAL_ACESSAR     = "configuracoes_gerais.acessar";

    // =========================================================================
    // MODULO: TAXA DE ENTREGA POR BAIRRO
    // =========================================================================
    public static final String TAXA_ENTREGA_ACESSAR     = "taxa_entrega.acessar";
    public static final String TAXA_ENTREGA_CRIAR       = "taxa_entrega.criar";
    public static final String TAXA_ENTREGA_EDITAR      = "taxa_entrega.editar";
    public static final String TAXA_ENTREGA_EXCLUIR     = "taxa_entrega.excluir";

    // =========================================================================
    // MODULO: PAINEL DE CHAMADOS
    // =========================================================================
    public static final String PAINEL_CHAMADOS_ACESSAR  = "painel_chamados.acessar";
    public static final String PAINEL_CHAMADOS_CRIAR    = "painel_chamados.criar";
    public static final String PAINEL_CHAMADOS_CHAMAR   = "painel_chamados.chamar";
    public static final String PAINEL_CHAMADOS_ATENDER  = "painel_chamados.atender";

    // =========================================================================
    // MODULO: GERENCIADOR DE CHAMADOS
    // =========================================================================
    public static final String GERENCIADOR_CHAMADOS_ACESSAR = "gerenciador_chamados.acessar";

    // =========================================================================
    // MODULO: GARCONS
    // =========================================================================
    public static final String GARCONS_ACESSAR          = "garcons.acessar";
    public static final String GARCONS_CRIAR            = "garcons.criar";
    public static final String GARCONS_EDITAR           = "garcons.editar";
    public static final String GARCONS_EXCLUIR          = "garcons.excluir";

    // =========================================================================
    // MODULO: MESAS (Cadastro)
    // =========================================================================
    public static final String MESAS_ACESSAR            = "mesas.acessar";
    public static final String MESAS_CRIAR              = "mesas.criar";
    public static final String MESAS_EDITAR             = "mesas.editar";
    public static final String MESAS_EXCLUIR            = "mesas.excluir";

    // =========================================================================
    // MODULO: GERENCIAR MESAS (Tela visual)
    // =========================================================================
    public static final String GERENCIAR_MESAS_ACESSAR  = "gerenciar_mesas.acessar";
    public static final String GERENCIAR_MESAS_LEITOR_CODIGO_BARRAS = "gerenciar_mesas.leitor_codigo_barras";

    // =========================================================================
    // MODULO: DASHBOARD - Botoes Garcons, Mesas e Gerenciar Mesas
    // =========================================================================
    public static final String DASHBOARD_BTN_GARCONS           = "dashboard.btn_garcons";
    public static final String DASHBOARD_BTN_CADASTRO_MESAS    = "dashboard.btn_cadastro_mesas";
    public static final String DASHBOARD_BTN_GERENCIAR_MESAS   = "dashboard.btn_gerenciar_mesas";

    // =========================================================================
    // MODULO: ARMARIOS PARA SAUNA (Cadastro)
    // =========================================================================
    public static final String ARMARIOS_SAUNA_ACESSAR   = "armarios_sauna.acessar";
    public static final String ARMARIOS_SAUNA_CRIAR     = "armarios_sauna.criar";
    public static final String ARMARIOS_SAUNA_EDITAR    = "armarios_sauna.editar";
    public static final String ARMARIOS_SAUNA_EXCLUIR   = "armarios_sauna.excluir";

    // =========================================================================
    // MODULO: GERENCIAR ARMARIOS SAUNA (Tela visual)
    // =========================================================================
    public static final String GERENCIAR_ARMARIOS_SAUNA_ACESSAR = "gerenciar_armarios_sauna.acessar";
    public static final String GERENCIAR_ARMARIOS_SAUNA_LEITOR_CODIGO_BARRAS = "gerenciar_armarios_sauna.leitor_codigo_barras";

    // =========================================================================
    // MODULO: DASHBOARD - Botoes Armarios Sauna
    // =========================================================================
    public static final String DASHBOARD_BTN_CADASTRO_ARMARIOS_SAUNA  = "dashboard.btn_cadastro_armarios_sauna";
    public static final String DASHBOARD_BTN_GERENCIAR_ARMARIOS_SAUNA = "dashboard.btn_gerenciar_armarios_sauna";
    public static final String DASHBOARD_BTN_ORDEM_SERVICO            = "dashboard.btn_ordem_servico";

    // =========================================================================
    // MODULO: ORDEM DE SERVICO
    // =========================================================================
    public static final String ORDEM_SERVICO_ACESSAR    = "ordem_servico.acessar";
    public static final String ORDEM_SERVICO_CRIAR      = "ordem_servico.criar";
    public static final String ORDEM_SERVICO_EDITAR     = "ordem_servico.editar";
    public static final String ORDEM_SERVICO_INATIVAR   = "ordem_servico.inativar";
    public static final String ORDEM_SERVICO_VER        = "ordem_servico.ver";
    public static final String ORDEM_SERVICO_IMPRIMIR   = "ordem_servico.imprimir";
    public static final String ORDEM_SERVICO_FOTOS      = "ordem_servico.fotos";
    public static final String ORDEM_SERVICO_LEITOR_CODIGO_BARRAS = "ordem_servico.leitor_codigo_barras";

    // =========================================================================
    // MODULO: SERVICOS (Cadastro de Servicos)
    // =========================================================================
    public static final String SERVICOS_ACESSAR         = "servicos.acessar";
    public static final String SERVICOS_CRIAR           = "servicos.criar";
    public static final String SERVICOS_EDITAR          = "servicos.editar";
    public static final String SERVICOS_EXCLUIR         = "servicos.excluir";
    // =========================================================================
    // MODULO: DASHBOARD - Botao Cadastro de Servicos
    // =========================================================================
    public static final String DASHBOARD_BTN_CADASTRO_SERVICO = "dashboard.btn_cadastro_servico";

    // =========================================================================
    // MODULO: ATUALIZAR SISTEMA
    // =========================================================================
    public static final String ATUALIZAR_SISTEMA_ACESSAR = "atualizar_sistema.acessar";

    // =========================================================================
    // MODULO: DASHBOARD - Botao Atualizar Sistema
    // =========================================================================
    public static final String DASHBOARD_BTN_ATUALIZAR   = "dashboard.btn_atualizar";

    // =========================================================================
    // MODULO: TROCAR SENHA
    // =========================================================================
    public static final String TROCAR_SENHA             = "conta.trocar_senha";

    // =========================================================================
    // METODO: Retorna TODAS as permissoes do sistema organizadas por modulo
    // =========================================================================

    public static List<Permissao> getTodasPermissoes() {
        List<Permissao> lista = new ArrayList<>();

        // DASHBOARD - Botoes da Tela Principal
        lista.add(new Permissao("Dashboard", "Botao Vendas", DASHBOARD_BTN_VENDAS, "Exibir botao Vendas no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Comandas", DASHBOARD_BTN_COMANDAS, "Exibir botao Comandas no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Produtos", DASHBOARD_BTN_PRODUTOS, "Exibir botao Produtos no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Gerenciar Produtos", DASHBOARD_BTN_GERENCIAR_PRODUTOS, "Exibir botao Gerenciar Produtos no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Clientes", DASHBOARD_BTN_CLIENTES, "Exibir botao Clientes no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Caixa", DASHBOARD_BTN_CAIXA, "Exibir botao Caixa no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Relatorios", DASHBOARD_BTN_RELATORIOS, "Exibir botao Relatorios no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Historico", DASHBOARD_BTN_HISTORICO, "Exibir botao Historico no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Empresa", DASHBOARD_BTN_EMPRESA, "Exibir botao Empresa no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Vendedores", DASHBOARD_BTN_VENDEDORES, "Exibir botao Vendedores no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Entregadores", DASHBOARD_BTN_ENTREGADORES, "Exibir botao Entregadores no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Usuarios", DASHBOARD_BTN_USUARIOS, "Exibir botao Usuarios no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Perfis", DASHBOARD_BTN_PERFIS, "Exibir botao Perfis e Permissoes no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Formas Pagamento", DASHBOARD_BTN_FORMAS_PAGAMENTO, "Exibir botao Formas de Pagamento no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Tipos Produto", DASHBOARD_BTN_TIPOS_PRODUTO, "Exibir botao Tipos de Produto no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Observacoes", DASHBOARD_BTN_OBSERVACOES, "Exibir botao Obs. Cupom no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Impressora", DASHBOARD_BTN_IMPRESSORA, "Exibir botao Impressora no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Backup", DASHBOARD_BTN_BACKUP, "Exibir botao Backup no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Licenca", DASHBOARD_BTN_LICENCA, "Exibir botao Licenca no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Trocar Senha", DASHBOARD_BTN_TROCAR_SENHA, "Exibir botao Trocar Senha no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Modo Entregador", DASHBOARD_BTN_MODO_ENTREGADOR, "Exibir botao Modo Entregador no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Gerenciar Entregas", DASHBOARD_BTN_GERENCIAR_ENTREGAS, "Exibir botao Gerenciar Entregas no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Contas a Receber", DASHBOARD_BTN_CONTAS_RECEBER, "Exibir botao Contas a Receber no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao WhatsApp Bot", DASHBOARD_BTN_WHATSBOT, "Exibir botao WhatsApp Bot no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Entrada Notas", DASHBOARD_BTN_ENTRADA_NOTAS, "Exibir botao Entrada de Notas no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Taxa Entrega", DASHBOARD_BTN_TAXA_ENTREGA, "Exibir botao Taxa de Entrega no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Sobre", DASHBOARD_BTN_SOBRE, "Exibir botao Sobre no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Painel Chamados", DASHBOARD_BTN_PAINEL_CHAMADOS, "Exibir botao Painel de Chamados no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Gerenciador Chamados", DASHBOARD_BTN_GERENCIADOR_CHAMADOS, "Exibir botao Gerenciador de Chamados no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Painel Cozinha", DASHBOARD_BTN_PAINEL_COZINHA, "Exibir botao Painel da Cozinha no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Web Cozinha", DASHBOARD_BTN_WEB_COZINHA, "Exibir botao Web Cozinha no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Painel Senhas", DASHBOARD_BTN_PAINEL_SENHAS, "Exibir botao Painel de Senhas no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Estacionamento", DASHBOARD_BTN_ESTACIONAMENTO, "Exibir botao Estacionamento no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Multiimpressoras", DASHBOARD_BTN_MULTIIMPRESSORAS, "Exibir botao Multiimpressoras no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Cardapio QR Code", DASHBOARD_BTN_CARDAPIO_QRCODE, "Exibir botao Cardapio QR Code no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Diagnostico", DASHBOARD_BTN_DIAGNOSTICO, "Exibir botao Diagnostico do Sistema no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Servidor MySQL", DASHBOARD_BTN_SERVIDOR_MYSQL, "Exibir botao Servidor MySQL no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Criar Banco MySQL", DASHBOARD_BTN_CRIAR_BANCO_MYSQL, "Exibir botao Criar Banco MySQL no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Usuarios MySQL", DASHBOARD_BTN_USUARIOS_MYSQL, "Exibir botao Usuarios MySQL no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao MySQL Espelho", DASHBOARD_BTN_MYSQL_ESPELHO, "Exibir botao Banco MySQL Espelho no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Agenda", DASHBOARD_BTN_AGENDA, "Exibir botao Agenda de Servicos no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Configuracoes", DASHBOARD_BTN_CONFIGURACOES, "Exibir botao Configuracoes no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Fornecedores", DASHBOARD_BTN_FORNECEDORES, "Exibir botao Fornecedores no menu principal"));

        // PAINEL DA COZINHA
        lista.add(new Permissao("Painel Cozinha", "Acessar", PAINEL_COZINHA_ACESSAR, "Acessar o painel da cozinha para acompanhar pedidos"));

        // PAINEL COZINHA WEB
        lista.add(new Permissao("Painel da Cozinha", "Web Cozinha", PAINEL_COZINHA_WEB, "Acessar a versao web do painel da cozinha"));

        // PAINEL DE SENHAS
        lista.add(new Permissao("Painel de Senhas", "Acessar", PAINEL_SENHAS_ACESSAR, "Acessar o painel de chamado de senhas"));
        lista.add(new Permissao("Painel de Senhas", "Chamar Senha", PAINEL_SENHAS_CHAMAR, "Chamar a proxima senha ou uma senha selecionada"));
        lista.add(new Permissao("Painel de Senhas", "Limpar", PAINEL_SENHAS_LIMPAR, "Limpar a fila/lista de senhas"));
        lista.add(new Permissao("Painel de Senhas", "Testar Som", PAINEL_SENHAS_TESTAR_SOM, "Testar som e voz do painel de senhas"));
        lista.add(new Permissao("Painel de Senhas", "Painel Web", PAINEL_SENHAS_WEB, "Acessar o painel web de senhas"));

        // ESTACIONAMENTO
        lista.add(new Permissao("Estacionamento", "Acessar", ESTACIONAMENTO_ACESSAR, "Acessar o gerenciamento de estacionamento"));
        lista.add(new Permissao("Estacionamento", "Registrar Entrada", ESTACIONAMENTO_ENTRADA, "Registrar chegada/entrada de veiculo"));
        lista.add(new Permissao("Estacionamento", "Finalizar Saida", ESTACIONAMENTO_FINALIZAR_SAIDA, "Finalizar saida e enviar ticket para pagamento"));
        lista.add(new Permissao("Estacionamento", "Cancelar Entrada", ESTACIONAMENTO_CANCELAR, "Cancelar entrada de veiculo"));
        lista.add(new Permissao("Estacionamento", "Historico", ESTACIONAMENTO_HISTORICO, "Consultar historico de estacionamento"));
        lista.add(new Permissao("Estacionamento", "Imprimir Chegada", ESTACIONAMENTO_IMPRIMIR_CHEGADA, "Imprimir comprovante de chegada do veiculo"));
        lista.add(new Permissao("Estacionamento", "Imprimir Entrega", ESTACIONAMENTO_IMPRIMIR_ENTREGA, "Imprimir comprovante de entrega do veiculo"));
        lista.add(new Permissao("Estacionamento", "Leitor de Placa", ESTACIONAMENTO_LEITOR_PLACA, "Usar leitor inteligente de placa"));
        lista.add(new Permissao("Estacionamento", "Configurar Valor", ESTACIONAMENTO_CONFIGURAR_VALOR, "Alterar valores/tarifas do estacionamento"));

        // MULTIIMPRESSORAS
        lista.add(new Permissao("Multiimpressoras", "Acessar", MULTIIMPRESSORAS_ACESSAR, "Acessar configuracao de impressao em multiimpressoras"));
        lista.add(new Permissao("Multiimpressoras", "Ativar/Desativar", MULTIIMPRESSORAS_ATIVAR, "Ativar ou desativar impressao em multiimpressoras"));
        lista.add(new Permissao("Multiimpressoras", "Criar Regra", MULTIIMPRESSORAS_CRIAR_REGRA, "Criar regra de impressao por categoria/tipo"));
        lista.add(new Permissao("Multiimpressoras", "Editar Regra", MULTIIMPRESSORAS_EDITAR_REGRA, "Editar regra de impressao por categoria/tipo"));
        lista.add(new Permissao("Multiimpressoras", "Excluir Regra", MULTIIMPRESSORAS_EXCLUIR_REGRA, "Excluir regra de impressao por categoria/tipo"));
        lista.add(new Permissao("Multiimpressoras", "Testar Impressora", MULTIIMPRESSORAS_TESTAR_IMPRESSORA, "Enviar teste para impressora configurada"));
        lista.add(new Permissao("Multiimpressoras", "Configurar Driver", MULTIIMPRESSORAS_CONFIGURAR_DRIVER, "Escolher driver/perfil ESC/POS por impressora"));

        // VENDAS
        lista.add(new Permissao("Vendas", "Acessar", VENDAS_ACESSAR, "Acessar a tela de vendas"));
        lista.add(new Permissao("Vendas", "Criar", VENDAS_CRIAR, "Criar nova venda e finalizar"));
        lista.add(new Permissao("Vendas", "Aplicar Desconto", VENDAS_APLICAR_DESCONTO, "Aplicar desconto na venda"));
        lista.add(new Permissao("Vendas", "Aplicar Acrescimo", VENDAS_APLICAR_ACRESCIMO, "Aplicar acrescimo na venda"));
        lista.add(new Permissao("Vendas", "Escolher Cliente", VENDAS_ESCOLHER_CLIENTE, "Selecionar cliente na venda"));
        lista.add(new Permissao("Vendas", "Escolher Vendedor", VENDAS_ESCOLHER_VENDEDOR, "Selecionar vendedor na venda"));
        lista.add(new Permissao("Vendas", "Escolher Entregador", VENDAS_ESCOLHER_ENTREGADOR, "Selecionar entregador na venda"));
        lista.add(new Permissao("Vendas", "Leitor Codigo Barras", VENDAS_LEITOR_CODIGO_BARRAS, "Usar leitor de codigo de barras na venda"));
        lista.add(new Permissao("Vendas", "Imprimir Canhoto Senha", VENDAS_IMPRIMIR_CANHOTO_SENHA, "Imprimir canhoto de senha na finalizacao"));
        lista.add(new Permissao("Vendas", "Imprimir Duas Vias", VENDAS_IMPRIMIR_DUAS_VIAS, "Imprimir duas vias do cupom"));
        lista.add(new Permissao("Vendas", "Exibir Senha Cupom", VENDAS_EXIBIR_SENHA_CUPOM, "Exibir senha dentro do cupom"));

        // HISTORICO
        lista.add(new Permissao("Historico", "Acessar", HISTORICO_ACESSAR, "Acessar historico de vendas"));
        lista.add(new Permissao("Historico", "Cancelar Venda", HISTORICO_CANCELAR_VENDA, "Cancelar vendas no historico"));
        lista.add(new Permissao("Historico", "Devolver Venda", HISTORICO_DEVOLVER_VENDA, "Fazer devolucao de venda no historico"));
        lista.add(new Permissao("Historico", "Reimprimir", HISTORICO_REIMPRIMIR, "Reimprimir cupom de venda"));
        lista.add(new Permissao("Historico", "Enviar WhatsApp", HISTORICO_ENVIAR_WHATSAPP, "Enviar cupom via WhatsApp"));

        // COMANDAS
        lista.add(new Permissao("Comandas", "Acessar", COMANDAS_ACESSAR, "Acessar a tela de comandas"));
        lista.add(new Permissao("Comandas", "Criar", COMANDAS_CRIAR, "Criar nova comanda"));
        lista.add(new Permissao("Comandas", "Editar", COMANDAS_EDITAR, "Editar comanda (add/remover itens)"));
        lista.add(new Permissao("Comandas", "Fechar", COMANDAS_FECHAR, "Fechar comanda e gerar venda"));
        lista.add(new Permissao("Comandas", "Cancelar", COMANDAS_CANCELAR, "Cancelar comanda"));
        lista.add(new Permissao("Comandas", "Imprimir", COMANDAS_IMPRIMIR, "Imprimir comanda"));
        lista.add(new Permissao("Comandas", "Leitor Codigo Barras", COMANDAS_LEITOR_CODIGO_BARRAS, "Usar leitor de codigo de barras em comandas"));

        // CAIXA
        lista.add(new Permissao("Caixa", "Acessar", CAIXA_ACESSAR, "Acessar a tela de caixa"));
        lista.add(new Permissao("Caixa", "Abrir", CAIXA_ABRIR, "Abrir caixa"));
        lista.add(new Permissao("Caixa", "Fechar", CAIXA_FECHAR, "Fechar caixa"));
        lista.add(new Permissao("Caixa", "Vale/Debito", CAIXA_VALE_DEBITO, "Adicionar vale/debito no caixa"));
        lista.add(new Permissao("Caixa", "Reimprimir Fechamento", CAIXA_REIMPRIMIR_FECHAMENTO, "Selecionar e reimprimir fechamento de caixa por data e numero"));

        // PRODUTOS
        lista.add(new Permissao("Produtos", "Acessar", PRODUTOS_ACESSAR, "Acessar cadastro de produtos"));
        lista.add(new Permissao("Produtos", "Criar", PRODUTOS_CRIAR, "Cadastrar novo produto"));
        lista.add(new Permissao("Produtos", "Editar", PRODUTOS_EDITAR, "Editar produto existente"));
        lista.add(new Permissao("Produtos", "Excluir", PRODUTOS_EXCLUIR, "Excluir/inativar produto"));

        // GERENCIAR PRODUTOS
        lista.add(new Permissao("Gerenciar Produtos", "Acessar", GERENCIAR_PRODUTOS_ACESSAR, "Acessar gerenciamento de produtos"));
        lista.add(new Permissao("Gerenciar Produtos", "Editar", GERENCIAR_PRODUTOS_EDITAR, "Editar produto no gerenciamento"));
        lista.add(new Permissao("Gerenciar Produtos", "Inativar/Reativar", GERENCIAR_PRODUTOS_INATIVAR, "Inativar ou reativar produtos"));

        // CLIENTES
        lista.add(new Permissao("Clientes", "Acessar", CLIENTES_ACESSAR, "Acessar cadastro de clientes"));
        lista.add(new Permissao("Clientes", "Criar", CLIENTES_CRIAR, "Cadastrar novo cliente"));
        lista.add(new Permissao("Clientes", "Editar", CLIENTES_EDITAR, "Editar cliente existente"));
        lista.add(new Permissao("Clientes", "Excluir", CLIENTES_EXCLUIR, "Excluir/inativar cliente"));

        // VENDEDORES
        lista.add(new Permissao("Vendedores", "Acessar", VENDEDORES_ACESSAR, "Acessar cadastro de vendedores"));
        lista.add(new Permissao("Vendedores", "Criar", VENDEDORES_CRIAR, "Cadastrar novo vendedor"));
        lista.add(new Permissao("Vendedores", "Editar", VENDEDORES_EDITAR, "Editar vendedor existente"));
        lista.add(new Permissao("Vendedores", "Excluir", VENDEDORES_EXCLUIR, "Excluir/inativar vendedor"));

        // ENTREGADORES
        lista.add(new Permissao("Entregadores", "Acessar", ENTREGADORES_ACESSAR, "Acessar cadastro de entregadores"));
        lista.add(new Permissao("Entregadores", "Criar", ENTREGADORES_CRIAR, "Cadastrar novo entregador"));
        lista.add(new Permissao("Entregadores", "Editar", ENTREGADORES_EDITAR, "Editar entregador existente"));
        lista.add(new Permissao("Entregadores", "Excluir", ENTREGADORES_EXCLUIR, "Excluir/inativar entregador"));

        // EMPRESA
        lista.add(new Permissao("Empresa", "Acessar", EMPRESA_ACESSAR, "Acessar dados da empresa"));
        lista.add(new Permissao("Empresa", "Editar", EMPRESA_EDITAR, "Editar dados da empresa"));

        // USUARIOS
        lista.add(new Permissao("Usuarios", "Acessar", USUARIOS_ACESSAR, "Acessar cadastro de usuarios"));
        lista.add(new Permissao("Usuarios", "Criar", USUARIOS_CRIAR, "Cadastrar novo usuario"));
        lista.add(new Permissao("Usuarios", "Editar", USUARIOS_EDITAR, "Editar usuario existente"));
        lista.add(new Permissao("Usuarios", "Excluir", USUARIOS_EXCLUIR, "Excluir/inativar usuario"));

        // PERFIS E PERMISSOES
        lista.add(new Permissao("Perfis", "Acessar", PERFIS_ACESSAR, "Acessar gerenciamento de perfis"));
        lista.add(new Permissao("Perfis", "Criar", PERFIS_CRIAR, "Criar novo perfil de acesso"));
        lista.add(new Permissao("Perfis", "Editar", PERFIS_EDITAR, "Editar perfil de acesso"));
        lista.add(new Permissao("Perfis", "Excluir", PERFIS_EXCLUIR, "Excluir perfil de acesso"));
        lista.add(new Permissao("Perfis", "Gerenciar Permissoes", PERFIS_GERENCIAR_PERMISSOES, "Atribuir/remover permissoes dos perfis"));
        lista.add(new Permissao("Perfis", "Permissoes por Usuario", PERFIS_PERMISSOES_USUARIO, "Gerenciar permissoes individuais por usuario"));

        // FORMAS DE PAGAMENTO
        lista.add(new Permissao("Formas Pagamento", "Acessar", FORMAS_PAGAMENTO_ACESSAR, "Acessar formas de pagamento"));
        lista.add(new Permissao("Formas Pagamento", "Criar", FORMAS_PAGAMENTO_CRIAR, "Cadastrar nova forma de pagamento"));
        lista.add(new Permissao("Formas Pagamento", "Editar", FORMAS_PAGAMENTO_EDITAR, "Editar forma de pagamento"));
        lista.add(new Permissao("Formas Pagamento", "Excluir", FORMAS_PAGAMENTO_EXCLUIR, "Excluir forma de pagamento"));

        // TIPOS DE PRODUTO
        lista.add(new Permissao("Tipos Produto", "Acessar", TIPOS_PRODUTO_ACESSAR, "Acessar tipos de produto"));
        lista.add(new Permissao("Tipos Produto", "Criar", TIPOS_PRODUTO_CRIAR, "Cadastrar novo tipo de produto"));
        lista.add(new Permissao("Tipos Produto", "Editar", TIPOS_PRODUTO_EDITAR, "Editar tipo de produto"));
        lista.add(new Permissao("Tipos Produto", "Excluir", TIPOS_PRODUTO_EXCLUIR, "Excluir tipo de produto"));

        // ADICIONAIS
        lista.add(new Permissao("Adicionais", "Acessar", ADICIONAIS_ACESSAR, "Acessar cadastro de adicionais"));
        lista.add(new Permissao("Adicionais", "Criar", ADICIONAIS_CRIAR, "Cadastrar novo adicional"));
        lista.add(new Permissao("Adicionais", "Editar", ADICIONAIS_EDITAR, "Editar adicional existente"));
        lista.add(new Permissao("Adicionais", "Excluir", ADICIONAIS_EXCLUIR, "Excluir/inativar adicional"));

        // DASHBOARD - Botao Adicionais
        lista.add(new Permissao("Dashboard", "Botao Adicionais", DASHBOARD_BTN_ADICIONAIS, "Exibir botao Adicionais no menu principal"));

        // OBSERVACOES DO CUPOM
        lista.add(new Permissao("Observacoes Cupom", "Acessar", OBSERVACOES_ACESSAR, "Acessar observacoes do cupom"));
        lista.add(new Permissao("Observacoes Cupom", "Criar", OBSERVACOES_CRIAR, "Cadastrar nova observacao"));
        lista.add(new Permissao("Observacoes Cupom", "Editar", OBSERVACOES_EDITAR, "Editar observacao"));
        lista.add(new Permissao("Observacoes Cupom", "Excluir", OBSERVACOES_EXCLUIR, "Excluir observacao"));

        // RELATORIOS
        lista.add(new Permissao("Relatorios", "Acessar", RELATORIOS_ACESSAR, "Acessar tela de relatorios"));
        lista.add(new Permissao("Relatorios", "Vendas", RELATORIOS_VENDAS, "Gerar relatorio de vendas"));
        lista.add(new Permissao("Relatorios", "Lucratividade", RELATORIOS_LUCRATIVIDADE, "Gerar relatorio de lucratividade"));
        lista.add(new Permissao("Relatorios", "Por Vendedor", RELATORIOS_VENDEDOR, "Gerar relatorio por vendedor"));
        lista.add(new Permissao("Relatorios", "Por Entregador", RELATORIOS_ENTREGADOR, "Gerar relatorio por entregador"));
        lista.add(new Permissao("Relatorios", "Por Cliente", RELATORIOS_CLIENTE, "Gerar relatorio por cliente"));
        lista.add(new Permissao("Relatorios", "Produtos", RELATORIOS_PRODUTOS, "Gerar relatorio de produtos"));
        lista.add(new Permissao("Relatorios", "Caixa", RELATORIOS_CAIXA, "Gerar relatorio de caixa"));
        lista.add(new Permissao("Relatorios", "Porcentagens de Garcom", RELATORIOS_GARCOM, "Consultar porcentagens e valores ganhos por garcom e data"));
        lista.add(new Permissao("Relatorios", "Taxas por Entregador", RELATORIOS_TAXAS_ENTREGADOR, "Consultar taxas de entrega por entregador e data"));
        lista.add(new Permissao("Relatorios", "Vales do Caixa", RELATORIOS_VALES_CAIXA, "Consultar vales por data, centro de custos e usuario"));
        lista.add(new Permissao("Relatorios", "Acoes dos Usuarios", RELATORIOS_AUDITORIA, "Consultar o historico completo de acoes dos usuarios"));

        // DIAGNOSTICO, MYSQL, AGENDA E CARDAPIO DIGITAL
        lista.add(new Permissao("Diagnostico", "Acessar", DIAGNOSTICO_ACESSAR, "Executar verificacoes tecnicas de integridade do sistema"));
        lista.add(new Permissao("Servidor MySQL", "Acessar Servidor", SERVIDOR_MYSQL_ACESSAR, "Iniciar e verificar o servidor MySQL integrado"));
        lista.add(new Permissao("Servidor MySQL", "Criar Banco", CRIAR_BANCO_MYSQL, "Criar banco de dados no servidor MySQL integrado"));
        lista.add(new Permissao("Servidor MySQL", "Gerenciar Usuarios", USUARIOS_MYSQL_ACESSAR, "Cadastrar e gerenciar usuarios do servidor MySQL"));
        lista.add(new Permissao("MySQL Espelho", "Acessar", MYSQL_ESPELHO_ACESSAR, "Acessar configuracao do banco externo de consulta"));
        lista.add(new Permissao("MySQL Espelho", "Sincronizar", MYSQL_ESPELHO_SINCRONIZAR, "Criar estrutura e sincronizar dados com o banco externo"));
        lista.add(new Permissao("Agenda", "Acessar", AGENDA_ACESSAR, "Acessar a agenda de servicos"));
        lista.add(new Permissao("Agenda", "Criar", AGENDA_CRIAR, "Agendar novo servico"));
        lista.add(new Permissao("Agenda", "Editar", AGENDA_EDITAR, "Editar agendamento existente"));
        lista.add(new Permissao("Agenda", "Cancelar", AGENDA_CANCELAR, "Cancelar agendamento existente"));
        lista.add(new Permissao("Cardapio QR Code", "Acessar", CARDAPIO_QRCODE_ACESSAR, "Gerar e administrar o cardapio digital por QR Code"));

        // CONFIGURACOES
        lista.add(new Permissao("Impressora", "Acessar", CONFIG_IMPRESSORA_ACESSAR, "Acessar config. de impressora"));
        lista.add(new Permissao("Impressora", "Editar", CONFIG_IMPRESSORA_EDITAR, "Alterar config. de impressora"));
        lista.add(new Permissao("Impressora", "Driver", CONFIG_IMPRESSORA_DRIVER, "Escolher driver/perfil ESC/POS da impressora termica"));

        // BACKUP
        lista.add(new Permissao("Backup", "Acessar", BACKUP_ACESSAR, "Acessar tela de backup"));
        lista.add(new Permissao("Backup", "Realizar", BACKUP_REALIZAR, "Realizar backup manualmente"));
        lista.add(new Permissao("Backup", "Restaurar", BACKUP_RESTAURAR, "Restaurar backup"));
        lista.add(new Permissao("Backup", "Configurar", BACKUP_CONFIGURAR, "Alterar config. de backup"));

        // LICENCA
        lista.add(new Permissao("Licenca", "Acessar", LICENCA_ACESSAR, "Acessar tela de licenca"));
        lista.add(new Permissao("Licenca", "Ativar", LICENCA_ATIVAR, "Ativar/renovar licenca"));

        // WHATSAPP BOT
        lista.add(new Permissao("WhatsApp Bot", "Acessar", WHATSBOT_ACESSAR, "Acessar WhatsApp Bot"));
        lista.add(new Permissao("WhatsApp Bot", "Configurar", WHATSBOT_CONFIGURAR, "Configurar WhatsApp Bot"));

        // ENTREGAS
        lista.add(new Permissao("Entregas", "Acessar", ENTREGAS_ACESSAR, "Acessar gerenciamento de entregas"));
        lista.add(new Permissao("Entregas", "Gerenciar", ENTREGAS_GERENCIAR, "Gerenciar entregas"));
        lista.add(new Permissao("Modo Entregador", "Acessar", MODO_ENTREGADOR_ACESSAR, "Acessar modo entregador"));

        // CONTAS A RECEBER
        lista.add(new Permissao("Contas a Receber", "Acessar", CONTAS_RECEBER_ACESSAR, "Acessar tela de contas a receber"));
        lista.add(new Permissao("Contas a Receber", "Receber", CONTAS_RECEBER_RECEBER, "Registrar recebimento de contas"));
        lista.add(new Permissao("Contas a Receber", "Cancelar", CONTAS_RECEBER_CANCELAR, "Cancelar contas a receber"));
        lista.add(new Permissao("Contas a Receber", "Relatorio", CONTAS_RECEBER_RELATORIO, "Gerar relatorio de contas a receber"));

        // ENTRADA DE NOTAS
        lista.add(new Permissao("Entrada de Notas", "Acessar", ENTRADA_NOTAS_ACESSAR, "Acessar tela de entrada de notas"));
        lista.add(new Permissao("Entrada de Notas", "Criar", ENTRADA_NOTAS_CRIAR, "Criar nova nota de entrada"));
        lista.add(new Permissao("Entrada de Notas", "Confirmar", ENTRADA_NOTAS_CONFIRMAR, "Confirmar nota e atualizar estoque"));
        lista.add(new Permissao("Entrada de Notas", "Cancelar", ENTRADA_NOTAS_CANCELAR, "Cancelar nota de entrada"));

        // FORNECEDORES
        lista.add(new Permissao("Fornecedores", "Acessar", FORNECEDORES_ACESSAR, "Acessar cadastro de fornecedores"));
        lista.add(new Permissao("Fornecedores", "Criar", FORNECEDORES_CRIAR, "Cadastrar novo fornecedor"));
        lista.add(new Permissao("Fornecedores", "Editar", FORNECEDORES_EDITAR, "Editar fornecedor existente"));
        lista.add(new Permissao("Fornecedores", "Excluir", FORNECEDORES_EXCLUIR, "Inativar ou reativar fornecedor"));

        // CONFIGURACOES GERAIS
        lista.add(new Permissao("Configuracoes Gerais", "Acessar", CONFIG_GERAL_ACESSAR, "Configurar taxas de servico de mesas, comandas e armarios"));

        // TAXA DE ENTREGA POR BAIRRO
        lista.add(new Permissao("Taxa Entrega", "Acessar", TAXA_ENTREGA_ACESSAR, "Acessar cadastro de taxas de entrega por bairro"));
        lista.add(new Permissao("Taxa Entrega", "Criar", TAXA_ENTREGA_CRIAR, "Cadastrar novo bairro com taxa"));
        lista.add(new Permissao("Taxa Entrega", "Editar", TAXA_ENTREGA_EDITAR, "Editar bairro e taxa"));
        lista.add(new Permissao("Taxa Entrega", "Excluir", TAXA_ENTREGA_EXCLUIR, "Excluir bairro"));

        // PAINEL DE CHAMADOS
        lista.add(new Permissao("Painel Chamados", "Acessar", PAINEL_CHAMADOS_ACESSAR, "Acessar o painel de chamados dos clientes"));
        lista.add(new Permissao("Painel Chamados", "Criar", PAINEL_CHAMADOS_CRIAR, "Criar novos chamados"));
        lista.add(new Permissao("Painel Chamados", "Chamar", PAINEL_CHAMADOS_CHAMAR, "Chamar clientes"));
        lista.add(new Permissao("Painel Chamados", "Atender", PAINEL_CHAMADOS_ATENDER, "Atender chamados"));

        // GERENCIADOR DE CHAMADOS
        lista.add(new Permissao("Gerenciador Chamados", "Acessar", GERENCIADOR_CHAMADOS_ACESSAR, "Acessar o gerenciador de chamadas com voz e sinal sonoro"));

        // GARCONS
        lista.add(new Permissao("Garcons", "Acessar", GARCONS_ACESSAR, "Acessar cadastro de garcons"));
        lista.add(new Permissao("Garcons", "Criar", GARCONS_CRIAR, "Cadastrar novo garcom"));
        lista.add(new Permissao("Garcons", "Editar", GARCONS_EDITAR, "Editar garcom existente"));
        lista.add(new Permissao("Garcons", "Excluir", GARCONS_EXCLUIR, "Excluir/inativar garcom"));

        // MESAS
        lista.add(new Permissao("Mesas", "Acessar", MESAS_ACESSAR, "Acessar cadastro de mesas"));
        lista.add(new Permissao("Mesas", "Criar", MESAS_CRIAR, "Cadastrar nova mesa"));
        lista.add(new Permissao("Mesas", "Editar", MESAS_EDITAR, "Editar mesa existente"));
        lista.add(new Permissao("Mesas", "Excluir", MESAS_EXCLUIR, "Excluir/inativar mesa"));

        // GERENCIAR MESAS
        lista.add(new Permissao("Gerenciar Mesas", "Acessar", GERENCIAR_MESAS_ACESSAR, "Acessar tela de gerenciamento visual de mesas"));
        lista.add(new Permissao("Gerenciar Mesas", "Leitor Codigo Barras", GERENCIAR_MESAS_LEITOR_CODIGO_BARRAS, "Usar leitor de codigo de barras na venda por mesa"));

        // DASHBOARD - Botoes Garcons, Mesas e Gerenciar Mesas
        lista.add(new Permissao("Dashboard", "Botao Garcons", DASHBOARD_BTN_GARCONS, "Exibir botao Garcons no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Cadastro Mesas", DASHBOARD_BTN_CADASTRO_MESAS, "Exibir botao Cadastro de Mesas no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Gerenciar Mesas", DASHBOARD_BTN_GERENCIAR_MESAS, "Exibir botao Gerenciar Mesas no menu principal"));

        // ARMARIOS PARA SAUNA
        lista.add(new Permissao("Armarios Sauna", "Acessar", ARMARIOS_SAUNA_ACESSAR, "Acessar cadastro de armarios para sauna"));
        lista.add(new Permissao("Armarios Sauna", "Criar", ARMARIOS_SAUNA_CRIAR, "Cadastrar novo armario para sauna"));
        lista.add(new Permissao("Armarios Sauna", "Editar", ARMARIOS_SAUNA_EDITAR, "Editar armario existente"));
        lista.add(new Permissao("Armarios Sauna", "Excluir", ARMARIOS_SAUNA_EXCLUIR, "Excluir/inativar armario"));

        // GERENCIAR ARMARIOS SAUNA
        lista.add(new Permissao("Gerenciar Armarios Sauna", "Acessar", GERENCIAR_ARMARIOS_SAUNA_ACESSAR, "Acessar tela de gerenciamento visual de armarios para sauna"));
        lista.add(new Permissao("Gerenciar Armarios Sauna", "Leitor Codigo Barras", GERENCIAR_ARMARIOS_SAUNA_LEITOR_CODIGO_BARRAS, "Usar leitor de codigo de barras na venda por armario/sauna"));

        // DASHBOARD - Botoes Armarios Sauna
        lista.add(new Permissao("Dashboard", "Botao Cadastro Armarios Sauna", DASHBOARD_BTN_CADASTRO_ARMARIOS_SAUNA, "Exibir botao Cadastro de Armarios Sauna no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Gerenciar Armarios Sauna", DASHBOARD_BTN_GERENCIAR_ARMARIOS_SAUNA, "Exibir botao Gerenciar Armarios Sauna no menu principal"));
        lista.add(new Permissao("Dashboard", "Botao Ordem de Servico", DASHBOARD_BTN_ORDEM_SERVICO, "Exibir botao Ordem de Servico no menu principal"));

        // ORDEM DE SERVICO
        lista.add(new Permissao("Ordem de Servico", "Acessar", ORDEM_SERVICO_ACESSAR, "Acessar modulo de ordem de servico"));
        lista.add(new Permissao("Ordem de Servico", "Criar", ORDEM_SERVICO_CRIAR, "Criar nova ordem de servico"));
        lista.add(new Permissao("Ordem de Servico", "Editar", ORDEM_SERVICO_EDITAR, "Editar ordem de servico existente"));
        lista.add(new Permissao("Ordem de Servico", "Inativar", ORDEM_SERVICO_INATIVAR, "Inativar ordem de servico"));
        lista.add(new Permissao("Ordem de Servico", "Ver", ORDEM_SERVICO_VER, "Ver ordem de servico completa com dados e fotos"));
        lista.add(new Permissao("Ordem de Servico", "Imprimir", ORDEM_SERVICO_IMPRIMIR, "Imprimir ordem de servico"));
        lista.add(new Permissao("Ordem de Servico", "Fotos", ORDEM_SERVICO_FOTOS, "Visualizar e gerenciar fotos da ordem de servico"));
        lista.add(new Permissao("Ordem de Servico", "Leitor Codigo Barras", ORDEM_SERVICO_LEITOR_CODIGO_BARRAS, "Usar leitor de codigo de barras na ordem de servico"));

        // SERVICOS
        lista.add(new Permissao("Servicos", "Acessar", SERVICOS_ACESSAR, "Acessar cadastro de servicos"));
        lista.add(new Permissao("Servicos", "Criar", SERVICOS_CRIAR, "Cadastrar novo servico"));
        lista.add(new Permissao("Servicos", "Editar", SERVICOS_EDITAR, "Editar servico existente"));
        lista.add(new Permissao("Servicos", "Excluir", SERVICOS_EXCLUIR, "Inativar servico"));

        // DASHBOARD - Botao Cadastro de Servicos
        lista.add(new Permissao("Dashboard", "Botao Cadastro de Servicos", DASHBOARD_BTN_CADASTRO_SERVICO, "Exibir botao Cadastro de Servicos no menu principal"));

        // ATUALIZAR SISTEMA
        lista.add(new Permissao("Atualizar Sistema", "Acessar", ATUALIZAR_SISTEMA_ACESSAR, "Acessar funcao de atualizar o sistema via FTP"));

        // DASHBOARD - Botao Atualizar
        lista.add(new Permissao("Dashboard", "Botao Atualizar Sistema", DASHBOARD_BTN_ATUALIZAR, "Exibir botao Atualizar Sistema no menu principal"));

        // TROCAR SENHA
        lista.add(new Permissao("Conta", "Trocar Senha", TROCAR_SENHA, "Trocar a propria senha"));

        // CONTAS A PAGAR
        lista.add(new Permissao("Contas a Pagar", "Acessar", CONTAS_PAGAR_ACESSAR, "Acessar tela de contas a pagar"));
        lista.add(new Permissao("Contas a Pagar", "Criar", CONTAS_PAGAR_CRIAR, "Criar nova conta a pagar"));
        lista.add(new Permissao("Contas a Pagar", "Editar", CONTAS_PAGAR_EDITAR, "Editar conta a pagar"));
        lista.add(new Permissao("Contas a Pagar", "Pagar", CONTAS_PAGAR_PAGAR, "Registrar pagamento de conta"));
        lista.add(new Permissao("Contas a Pagar", "Cancelar", CONTAS_PAGAR_CANCELAR, "Cancelar conta a pagar"));
        lista.add(new Permissao("Contas a Pagar", "Relatorio", CONTAS_PAGAR_RELATORIO, "Gerar relatorio de contas a pagar"));
        lista.add(new Permissao("Dashboard", "Botao Contas a Pagar", DASHBOARD_BTN_CONTAS_PAGAR, "Exibir botao Contas a Pagar no menu principal"));

        // CAIXAS NOMINAIS
        lista.add(new Permissao("Caixas", "Acessar", CAIXAS_NOMINAIS_ACESSAR, "Acessar cadastro de caixas"));
        lista.add(new Permissao("Caixas", "Criar", CAIXAS_NOMINAIS_CRIAR, "Criar novo caixa"));
        lista.add(new Permissao("Caixas", "Editar", CAIXAS_NOMINAIS_EDITAR, "Editar caixa existente"));
        lista.add(new Permissao("Caixas", "Excluir", CAIXAS_NOMINAIS_EXCLUIR, "Excluir/inativar caixa"));
        lista.add(new Permissao("Dashboard", "Botao Caixas", DASHBOARD_BTN_CAIXAS_NOMINAIS, "Exibir botao Caixas no menu principal"));

        // TURNOS
        lista.add(new Permissao("Turnos", "Acessar", TURNOS_ACESSAR, "Acessar cadastro de turnos"));
        lista.add(new Permissao("Turnos", "Criar", TURNOS_CRIAR, "Criar novo turno"));
        lista.add(new Permissao("Turnos", "Editar", TURNOS_EDITAR, "Editar turno existente"));
        lista.add(new Permissao("Turnos", "Excluir", TURNOS_EXCLUIR, "Excluir/inativar turno"));
        lista.add(new Permissao("Dashboard", "Botao Turnos", DASHBOARD_BTN_TURNOS, "Exibir botao Turnos no menu principal"));

        // VINCULOS
        lista.add(new Permissao("Vinculos", "Acessar", VINCULOS_ACESSAR, "Acessar vinculos usuario-caixa-turno"));
        lista.add(new Permissao("Vinculos", "Criar", VINCULOS_CRIAR, "Criar vinculo usuario-caixa-turno"));
        lista.add(new Permissao("Vinculos", "Excluir", VINCULOS_EXCLUIR, "Excluir vinculo"));
        lista.add(new Permissao("Vinculos", "Relatorio", VINCULOS_RELATORIO, "Gerar relatorio de vinculos"));
        lista.add(new Permissao("Dashboard", "Botao Vinculos", DASHBOARD_BTN_VINCULOS, "Exibir botao Vinculos no menu principal"));

        return lista;
    }

    /**
     * Retorna as permissoes agrupadas por modulo.
     */
    public static Map<String, List<Permissao>> getPermissoesPorModulo() {
        Map<String, List<Permissao>> mapa = new LinkedHashMap<>();
        for (Permissao p : getTodasPermissoes()) {
            String modulo = p.getModulo();
            if (!mapa.containsKey(modulo)) {
                mapa.put(modulo, new ArrayList<>());
            }
            mapa.get(modulo).add(p);
        }
        return mapa;
    }

    /**
     * Retorna todas as chaves de permissao (para o perfil Administrador).
     */
    public static List<String> getTodasChaves() {
        List<String> chaves = new ArrayList<>();
        for (Permissao p : getTodasPermissoes()) {
            chaves.add(p.getChave());
        }
        return chaves;
    }

    /**
     * v7.0.1 - Retorna a lista de botoes do dashboard com seus nomes amigaveis
     * e as chaves de permissao correspondentes (dashboard + modulo).
     * Usado para o dialog de personalizacao do perfil "Personalizavel".
     *
     * Cada entrada contem: [nome_amigavel, chave_dashboard, chave_modulo]
     */
    public static List<String[]> getDashboardBotoes() {
        List<String[]> botoes = new ArrayList<>();
        botoes.add(new String[]{"Vendas", DASHBOARD_BTN_VENDAS, VENDAS_ACESSAR});
        botoes.add(new String[]{"Comandas", DASHBOARD_BTN_COMANDAS, COMANDAS_ACESSAR});
        botoes.add(new String[]{"Produtos", DASHBOARD_BTN_PRODUTOS, PRODUTOS_ACESSAR});
        botoes.add(new String[]{"Gerenciar Produtos", DASHBOARD_BTN_GERENCIAR_PRODUTOS, GERENCIAR_PRODUTOS_ACESSAR});
        botoes.add(new String[]{"Clientes", DASHBOARD_BTN_CLIENTES, CLIENTES_ACESSAR});
        botoes.add(new String[]{"Caixa", DASHBOARD_BTN_CAIXA, CAIXA_ACESSAR});
        botoes.add(new String[]{"Relatorios", DASHBOARD_BTN_RELATORIOS, RELATORIOS_ACESSAR});
        botoes.add(new String[]{"Historico", DASHBOARD_BTN_HISTORICO, HISTORICO_ACESSAR});
        botoes.add(new String[]{"Empresa", DASHBOARD_BTN_EMPRESA, EMPRESA_ACESSAR});
        botoes.add(new String[]{"Vendedores", DASHBOARD_BTN_VENDEDORES, VENDEDORES_ACESSAR});
        botoes.add(new String[]{"Entregadores", DASHBOARD_BTN_ENTREGADORES, ENTREGADORES_ACESSAR});
        botoes.add(new String[]{"Usuarios", DASHBOARD_BTN_USUARIOS, USUARIOS_ACESSAR});
        botoes.add(new String[]{"Perfis e Permissoes", DASHBOARD_BTN_PERFIS, PERFIS_ACESSAR});
        botoes.add(new String[]{"Formas de Pagamento", DASHBOARD_BTN_FORMAS_PAGAMENTO, FORMAS_PAGAMENTO_ACESSAR});
        botoes.add(new String[]{"Tipos de Produto", DASHBOARD_BTN_TIPOS_PRODUTO, TIPOS_PRODUTO_ACESSAR});
        botoes.add(new String[]{"Adicionais", DASHBOARD_BTN_ADICIONAIS, ADICIONAIS_ACESSAR});
        botoes.add(new String[]{"Obs. Cupom", DASHBOARD_BTN_OBSERVACOES, OBSERVACOES_ACESSAR});
        botoes.add(new String[]{"Impressora", DASHBOARD_BTN_IMPRESSORA, CONFIG_IMPRESSORA_ACESSAR});
        botoes.add(new String[]{"Multiimpressoras", DASHBOARD_BTN_MULTIIMPRESSORAS, MULTIIMPRESSORAS_ACESSAR});
        botoes.add(new String[]{"Backup", DASHBOARD_BTN_BACKUP, BACKUP_ACESSAR});
        botoes.add(new String[]{"Licenca", DASHBOARD_BTN_LICENCA, LICENCA_ACESSAR});
        botoes.add(new String[]{"Trocar Senha", DASHBOARD_BTN_TROCAR_SENHA, TROCAR_SENHA});
        botoes.add(new String[]{"Modo Entregador", DASHBOARD_BTN_MODO_ENTREGADOR, MODO_ENTREGADOR_ACESSAR});
        botoes.add(new String[]{"Gerenciar Entregas", DASHBOARD_BTN_GERENCIAR_ENTREGAS, ENTREGAS_ACESSAR});
        botoes.add(new String[]{"Contas a Receber", DASHBOARD_BTN_CONTAS_RECEBER, CONTAS_RECEBER_ACESSAR});
        botoes.add(new String[]{"WhatsApp Bot", DASHBOARD_BTN_WHATSBOT, WHATSBOT_ACESSAR});
        botoes.add(new String[]{"Entrada de Notas", DASHBOARD_BTN_ENTRADA_NOTAS, ENTRADA_NOTAS_ACESSAR});
        botoes.add(new String[]{"Taxa de Entrega", DASHBOARD_BTN_TAXA_ENTREGA, TAXA_ENTREGA_ACESSAR});
        botoes.add(new String[]{"Sobre", DASHBOARD_BTN_SOBRE, null});
        botoes.add(new String[]{"Painel de Chamados", DASHBOARD_BTN_PAINEL_CHAMADOS, PAINEL_CHAMADOS_ACESSAR});
        botoes.add(new String[]{"Gerenciador de Chamados", DASHBOARD_BTN_GERENCIADOR_CHAMADOS, GERENCIADOR_CHAMADOS_ACESSAR});
        botoes.add(new String[]{"Garcons", DASHBOARD_BTN_GARCONS, GARCONS_ACESSAR});
        botoes.add(new String[]{"Cadastro de Mesas", DASHBOARD_BTN_CADASTRO_MESAS, MESAS_ACESSAR});
        botoes.add(new String[]{"Gerenciar Mesas", DASHBOARD_BTN_GERENCIAR_MESAS, GERENCIAR_MESAS_ACESSAR});
        botoes.add(new String[]{"Painel da Cozinha", DASHBOARD_BTN_PAINEL_COZINHA, PAINEL_COZINHA_ACESSAR});
        botoes.add(new String[]{"Web Cozinha", DASHBOARD_BTN_WEB_COZINHA, PAINEL_COZINHA_WEB});
        botoes.add(new String[]{"Painel de Senhas", DASHBOARD_BTN_PAINEL_SENHAS, PAINEL_SENHAS_ACESSAR});
        botoes.add(new String[]{"Estacionamento", DASHBOARD_BTN_ESTACIONAMENTO, ESTACIONAMENTO_ACESSAR});
        botoes.add(new String[]{"Cadastro Armarios Sauna", DASHBOARD_BTN_CADASTRO_ARMARIOS_SAUNA, ARMARIOS_SAUNA_ACESSAR});
        botoes.add(new String[]{"Gerenciar Armarios Sauna", DASHBOARD_BTN_GERENCIAR_ARMARIOS_SAUNA, GERENCIAR_ARMARIOS_SAUNA_ACESSAR});
        botoes.add(new String[]{"Ordem de Servico", DASHBOARD_BTN_ORDEM_SERVICO, ORDEM_SERVICO_ACESSAR});
        botoes.add(new String[]{"Cadastro de Servicos", DASHBOARD_BTN_CADASTRO_SERVICO, SERVICOS_ACESSAR});
        botoes.add(new String[]{"Atualizar Sistema", DASHBOARD_BTN_ATUALIZAR, ATUALIZAR_SISTEMA_ACESSAR});
        botoes.add(new String[]{"Cardapio QR Code", DASHBOARD_BTN_CARDAPIO_QRCODE, CARDAPIO_QRCODE_ACESSAR});
        botoes.add(new String[]{"Diagnostico", DASHBOARD_BTN_DIAGNOSTICO, DIAGNOSTICO_ACESSAR});
        botoes.add(new String[]{"Servidor MySQL", DASHBOARD_BTN_SERVIDOR_MYSQL, SERVIDOR_MYSQL_ACESSAR});
        botoes.add(new String[]{"Criar Banco MySQL", DASHBOARD_BTN_CRIAR_BANCO_MYSQL, CRIAR_BANCO_MYSQL});
        botoes.add(new String[]{"Usuarios MySQL", DASHBOARD_BTN_USUARIOS_MYSQL, USUARIOS_MYSQL_ACESSAR});
        botoes.add(new String[]{"MySQL Espelho", DASHBOARD_BTN_MYSQL_ESPELHO, MYSQL_ESPELHO_ACESSAR});
        botoes.add(new String[]{"Agenda de Servicos", DASHBOARD_BTN_AGENDA, AGENDA_ACESSAR});
        botoes.add(new String[]{"Configuracoes", DASHBOARD_BTN_CONFIGURACOES, CONFIG_GERAL_ACESSAR});
        botoes.add(new String[]{"Fornecedores", DASHBOARD_BTN_FORNECEDORES, FORNECEDORES_ACESSAR});
        botoes.add(new String[]{"Contas a Pagar", DASHBOARD_BTN_CONTAS_PAGAR, CONTAS_PAGAR_ACESSAR});
        botoes.add(new String[]{"Caixas", DASHBOARD_BTN_CAIXAS_NOMINAIS, CAIXAS_NOMINAIS_ACESSAR});
        botoes.add(new String[]{"Turnos", DASHBOARD_BTN_TURNOS, TURNOS_ACESSAR});
        botoes.add(new String[]{"Vinculos", DASHBOARD_BTN_VINCULOS, VINCULOS_ACESSAR});
        return botoes;
    }
}
