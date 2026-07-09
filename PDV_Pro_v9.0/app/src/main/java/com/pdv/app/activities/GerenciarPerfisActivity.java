package com.pdv.app.activities;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.models.Perfil;
import com.pdv.app.models.Permissao;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.permissions.PermissionManager;
import com.pdv.app.utils.AnimUtils;
import com.pdv.app.utils.ErrorHandler;

import java.sql.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * Tela de Gerenciamento de Perfis e Permissoes.
 * 
 * v6.0.0 - Sistema de Permissoes Avancado:
 *   - Gerenciamento de perfis (CRUD)
 *   - Gerenciamento de permissoes por perfil (checkboxes organizados por modulo)
 *   - Gerenciamento de permissoes individuais por usuario (overrides)
 *   - Botoes de atalho: Marcar Todos, Desmarcar Todos, por modulo
 *   - Indicador visual de overrides (permissoes adicionadas/removidas)
 *   - Aba de Perfis e aba de Usuarios
 */
public class GerenciarPerfisActivity extends BaseActivity {
    private static final String TAG = "GerenciarPerfis";
    private RecyclerView recyclerView;
    private GenericAdapter<Perfil> perfilAdapter;
    private PermissionManager pm;

    // Cache de contagem de usuarios por perfil
    private Map<Integer, Integer> contagemUsuariosPorPerfil = new HashMap<>();

    // Abas
    private Button btnTabPerfis, btnTabUsuarios;
    private boolean mostraUsuarios = false;

    // Adapter de usuarios
    private GenericAdapter<Map<String, Object>> usuarioAdapter;
    private List<Map<String, Object>> listaUsuarios = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);

        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.PERFIS_ACESSAR)) {
            return;
        }

        pm = PermissionManager.getInstance(this);

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Perfis e Permissoes");

        // Esconder busca padrao
        EditText etBusca = findViewById(R.id.etBusca);
        if (etBusca != null) etBusca.setVisibility(View.GONE);
        View btnBuscar = findViewById(R.id.btnBuscar);
        if (btnBuscar != null) btnBuscar.setVisibility(View.GONE);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Criar abas acima do recycler
        criarAbas();

        setupPerfilAdapter();
        setupUsuarioAdapter();

        recyclerView.setAdapter(perfilAdapter);

        // Botao Novo
        View btnNovo = findViewById(R.id.btnNovo);
        PermissionHelper.controlarVisibilidade(this, btnNovo, PermissionConstants.PERFIS_CRIAR);
        if (btnNovo != null) {
            btnNovo.setOnClickListener(v -> {
                if (mostraUsuarios) {
                    // Na aba de usuarios, nao tem "Novo"
                    showError("Para criar usuarios, acesse o menu Usuarios.");
                } else {
                    if (PermissionHelper.verificar(this, PermissionConstants.PERFIS_CRIAR)) {
                        showEditDialog(null);
                    }
                }
            });
        }

        loadData();
    }

    /**
     * Cria as abas "Perfis" e "Permissoes por Usuario" acima da lista.
     */
    private void criarAbas() {
        // Encontrar o container principal e adicionar abas antes do recycler
        View mainContent = findViewById(R.id.recyclerView);
        if (mainContent == null || mainContent.getParent() == null) return;

        LinearLayout parent = (LinearLayout) mainContent.getParent();

        // Criar layout das abas
        LinearLayout tabLayout = new LinearLayout(this);
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabLayout.setGravity(Gravity.CENTER);
        tabLayout.setPadding(16, 8, 16, 8);
        tabLayout.setBackgroundColor(Color.parseColor("#0A0E27"));

        btnTabPerfis = criarBotaoAba("Perfis", true);
        btnTabUsuarios = criarBotaoAba("Permissoes por Usuario", false);

        tabLayout.addView(btnTabPerfis);
        tabLayout.addView(btnTabUsuarios);

        btnTabPerfis.setOnClickListener(v -> {
            if (mostraUsuarios) {
                mostraUsuarios = false;
                atualizarAbas();
                recyclerView.setAdapter(perfilAdapter);
                // Mostrar botao Novo para perfis
                View btnNovo = findViewById(R.id.btnNovo);
                if (btnNovo != null) {
                    PermissionHelper.controlarVisibilidade(this, btnNovo, PermissionConstants.PERFIS_CRIAR);
                }
                loadData();
            }
        });

        btnTabUsuarios.setOnClickListener(v -> {
            if (!PermissionHelper.verificar(this, PermissionConstants.PERFIS_PERMISSOES_USUARIO)) {
                return;
            }
            if (!mostraUsuarios) {
                mostraUsuarios = true;
                atualizarAbas();
                recyclerView.setAdapter(usuarioAdapter);
                // Esconder botao Novo na aba de usuarios
                View btnNovo = findViewById(R.id.btnNovo);
                if (btnNovo != null) btnNovo.setVisibility(View.GONE);
                loadUsuarios();
            }
        });

        // Inserir abas antes do recycler
        int recyclerIndex = parent.indexOfChild(mainContent);
        parent.addView(tabLayout, recyclerIndex);
    }

    private Button criarBotaoAba(String texto, boolean ativo) {
        Button btn = new Button(this);
        btn.setText(texto);
        btn.setTextSize(12);
        btn.setAllCaps(false);
        btn.setPadding(24, 12, 24, 12);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(lp);

        aplicarEstiloAba(btn, ativo);
        return btn;
    }

    private void atualizarAbas() {
        aplicarEstiloAba(btnTabPerfis, !mostraUsuarios);
        aplicarEstiloAba(btnTabUsuarios, mostraUsuarios);
    }

    private void aplicarEstiloAba(Button btn, boolean ativo) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(20f);

        if (ativo) {
            bg.setColor(Color.parseColor("#00BCD4"));
            btn.setTextColor(Color.WHITE);
            btn.setTypeface(null, Typeface.BOLD);
        } else {
            bg.setColor(Color.parseColor("#1A1F3D"));
            bg.setStroke(2, Color.parseColor("#00BCD4"));
            btn.setTextColor(Color.parseColor("#90A4AE"));
            btn.setTypeface(null, Typeface.NORMAL);
        }
        btn.setBackground(bg);
    }

    // =========================================================================
    // ADAPTER DE PERFIS
    // =========================================================================

    private void setupPerfilAdapter() {
        perfilAdapter = new GenericAdapter<>(R.layout.item_list_generic, (holder, item, pos) -> {
            String line1 = item.getNome();
            if (item.isSistematico()) {
                line1 += "  \uD83D\uDD12";
            }
            if (item.isPersonalizavel()) {
                line1 += "  \uD83D\uDD27";
            }
            holder.setText(R.id.tvLine1, line1);

            String desc = item.getDescricao() != null ? item.getDescricao() : "";
            try {
                Integer qtdUsuarios = contagemUsuariosPorPerfil.get(item.getId());
                int qtd = qtdUsuarios != null ? qtdUsuarios : 0;
                desc += (desc.isEmpty() ? "" : " | ") + qtd + " usuario(s)";
            } catch (Exception ignored) {}
            holder.setText(R.id.tvLine2, desc);

            ImageView iv = holder.find(R.id.ivFoto);
            if (iv != null) iv.setVisibility(View.GONE);

            Button btnEditar = holder.find(R.id.btnEditar);
            if (btnEditar != null) {
                if (!item.isSistematico() && PermissionHelper.verificarSilencioso(this, PermissionConstants.PERFIS_EDITAR)) {
                    btnEditar.setVisibility(View.VISIBLE);
                    btnEditar.setOnClickListener(v -> showEditDialog(item));
                } else {
                    btnEditar.setVisibility(View.GONE);
                }
            }

            Button btnInativar = holder.find(R.id.btnInativar);
            if (btnInativar != null) {
                if (!item.isSistematico() && PermissionHelper.verificarSilencioso(this, PermissionConstants.PERFIS_EXCLUIR)) {
                    btnInativar.setVisibility(View.VISIBLE);
                    btnInativar.setText("Excluir");
                    btnInativar.setOnClickListener(v -> {
                        showConfirm("Excluir Perfil",
                                "Deseja excluir o perfil \"" + item.getNome() + "\"?\n\n"
                                + "Usuarios com este perfil perderao suas permissoes.",
                                () -> excluirPerfil(item.getId()));
                    });
                } else {
                    btnInativar.setVisibility(View.GONE);
                }
            }
        });

        perfilAdapter.setOnItemClickListener((item, pos) -> showPerfilOptions(item));
        perfilAdapter.setOnItemLongClickListener((item, pos) -> {
            if (!item.isSistematico()) {
                if (PermissionHelper.verificar(this, PermissionConstants.PERFIS_EXCLUIR)) {
                    showConfirm("Excluir Perfil",
                            "Deseja excluir o perfil \"" + item.getNome() + "\"?\n\n"
                            + "Usuarios com este perfil perderao suas permissoes.",
                            () -> excluirPerfil(item.getId()));
                }
            } else {
                showError("Perfis do sistema nao podem ser excluidos.");
            }
        });
    }

    // =========================================================================
    // ADAPTER DE USUARIOS (para permissoes individuais)
    // =========================================================================

    @SuppressWarnings("unchecked")
    private void setupUsuarioAdapter() {
        usuarioAdapter = new GenericAdapter<>(R.layout.item_list_generic, (holder, item, pos) -> {
            String nome = (String) item.get("nome");
            String login = (String) item.get("login");
            String perfilNome = (String) item.get("perfil_nome");
            int userId = (int) item.get("id");
            boolean sistematico = item.get("sistematico") != null && (boolean) item.get("sistematico");

            String line1 = nome;
            if (sistematico) {
                line1 += "  \uD83D\uDD12 Admin";
            }
            holder.setText(R.id.tvLine1, line1);

            // Carregar contagem de overrides em background
            String line2 = "Login: " + login + " | Perfil: " + perfilNome;
            Integer overrides = (Integer) item.get("overrides_count");
            if (overrides != null && overrides > 0) {
                line2 += " | " + overrides + " override(s)";
            }
            holder.setText(R.id.tvLine2, line2);

            ImageView iv = holder.find(R.id.ivFoto);
            if (iv != null) iv.setVisibility(View.GONE);

            Button btnEditar = holder.find(R.id.btnEditar);
            if (btnEditar != null) {
                if (!sistematico) {
                    btnEditar.setVisibility(View.VISIBLE);
                    btnEditar.setText("Permissoes");
                    btnEditar.setOnClickListener(v -> showPermissoesUsuarioDialog(userId, nome, perfilNome));
                } else {
                    btnEditar.setVisibility(View.VISIBLE);
                    btnEditar.setText("Visualizar");
                    btnEditar.setOnClickListener(v -> showError("Administradores sistematicos possuem TODAS as permissoes.\nNao e possivel alterar."));
                }
            }

            Button btnInativar = holder.find(R.id.btnInativar);
            if (btnInativar != null) {
                if (!sistematico) {
                    btnInativar.setVisibility(View.VISIBLE);
                    btnInativar.setText("Limpar");
                    btnInativar.setOnClickListener(v -> {
                        showConfirm("Limpar Overrides",
                                "Deseja remover todas as permissoes individuais de \"" + nome + "\"?\n\n"
                                + "O usuario voltara a ter apenas as permissoes do perfil \"" + perfilNome + "\".",
                                () -> limparOverridesUsuario(userId, nome));
                    });
                } else {
                    btnInativar.setVisibility(View.GONE);
                }
            }
        });

        usuarioAdapter.setOnItemClickListener((item, pos) -> {
            int userId = (int) item.get("id");
            String nome = (String) item.get("nome");
            String perfilNome = (String) item.get("perfil_nome");
            boolean sistematico = item.get("sistematico") != null && (boolean) item.get("sistematico");

            if (sistematico) {
                showError("Administradores sistematicos possuem TODAS as permissoes.\nNao e possivel alterar.");
            } else {
                showPermissoesUsuarioDialog(userId, nome, perfilNome);
            }
        });
    }

    // =========================================================================
    // CARREGAMENTO DE DADOS
    // =========================================================================

    @Override
    protected void onResume() {
        super.onResume();
        if (mostraUsuarios) {
            loadUsuarios();
        } else {
            loadData();
        }
    }

    private void loadData() {
        showLoading("Carregando perfis...");
        new Thread(() -> {
            try {
                List<Perfil> perfis = pm.listarPerfis();

                Map<Integer, Integer> contagem = new HashMap<>();
                for (Perfil p : perfis) {
                    try {
                        int qtd = pm.contarUsuariosPorPerfil(p.getId());
                        contagem.put(p.getId(), qtd);
                    } catch (Exception e) {
                        Log.w(TAG, "Erro ao contar usuarios do perfil " + p.getId() + ": " + e.getMessage());
                        contagem.put(p.getId(), 0);
                    }
                }

                hideLoading();
                runOnUiThread(() -> {
                    contagemUsuariosPorPerfil = contagem;
                    perfilAdapter.setItems(perfis);
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private void loadUsuarios() {
        showLoading("Carregando usuarios...");
        new Thread(() -> {
            try {
                List<Map<String, Object>> usuarios = pm.listarUsuariosComPerfil();

                // Carregar contagem de overrides para cada usuario
                for (Map<String, Object> user : usuarios) {
                    int userId = (int) user.get("id");
                    try {
                        int overrides = pm.contarOverridesDoUsuario(userId);
                        user.put("overrides_count", overrides);
                    } catch (Exception e) {
                        user.put("overrides_count", 0);
                    }
                }

                hideLoading();
                runOnUiThread(() -> {
                    listaUsuarios = usuarios;
                    usuarioAdapter.setItems(usuarios);
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    // =========================================================================
    // OPCOES DO PERFIL
    // =========================================================================

    private void showPerfilOptions(Perfil perfil) {
        List<String> opcoes = new ArrayList<>();
        List<Runnable> acoes = new ArrayList<>();

        if (PermissionHelper.verificarSilencioso(this, PermissionConstants.PERFIS_GERENCIAR_PERMISSOES)) {
            opcoes.add("\uD83D\uDD11 Gerenciar Permissoes");
            acoes.add(() -> showPermissoesDialog(perfil));
        }

        // Opcao especial para perfil Personalizavel: configurar botoes do dashboard
        if (perfil.isPersonalizavel() && PermissionHelper.verificarSilencioso(this, PermissionConstants.PERFIS_GERENCIAR_PERMISSOES)) {
            opcoes.add("\uD83D\uDD27 Personalizar Botoes do Dashboard");
            acoes.add(() -> showPersonalizarDashboardDialog(perfil));
        }

        if (!perfil.isSistematico() && PermissionHelper.verificarSilencioso(this, PermissionConstants.PERFIS_EDITAR)) {
            opcoes.add("\u270F\uFE0F Editar Perfil");
            acoes.add(() -> showEditDialog(perfil));
        }

        if (!perfil.isSistematico() && PermissionHelper.verificarSilencioso(this, PermissionConstants.PERFIS_EXCLUIR)) {
            opcoes.add("\uD83D\uDDD1\uFE0F Excluir Perfil");
            acoes.add(() -> showConfirm("Excluir Perfil",
                    "Deseja excluir o perfil \"" + perfil.getNome() + "\"?",
                    () -> excluirPerfil(perfil.getId())));
        }

        if (opcoes.isEmpty()) {
            showError("Voce nao tem permissao para gerenciar este perfil.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(perfil.getNome())
                .setItems(opcoes.toArray(new String[0]), (d, w) -> acoes.get(w).run())
                .show();
    }

    // =========================================================================
    // DIALOG DE EDICAO DE PERFIL
    // =========================================================================

    private void showEditDialog(Perfil perfil) {
        try {
            if (isFinishing() || isDestroyed()) return;

            View dialogView = getLayoutInflater().inflate(R.layout.dialog_generic_form, null);
            LinearLayout container = dialogView.findViewById(R.id.formContainer);

            EditText etNome = criarInput(container, "Nome do Perfil");
            EditText etDescricao = criarInput(container, "Descricao (opcional)");

            if (perfil != null) {
                etNome.setText(perfil.getNome());
                etDescricao.setText(perfil.getDescricao());
            }

            new AlertDialog.Builder(this)
                    .setTitle(perfil == null ? "Novo Perfil" : "Editar Perfil")
                    .setView(dialogView)
                    .setPositiveButton("Salvar", (d, w) -> {
                        String nome = etNome.getText().toString().trim();
                        String descricao = etDescricao.getText().toString().trim();
                        if (nome.isEmpty()) {
                            showError("O nome do perfil e obrigatorio.");
                            return;
                        }
                        salvarPerfil(perfil != null ? perfil.getId() : 0, nome, descricao);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao exibir dialog de edicao", e);
            showError("Erro ao abrir formulario. Tente novamente.");
        }
    }

    private EditText criarInput(LinearLayout container, String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF90A4AE);
        et.setBackgroundResource(R.drawable.input_bg);
        et.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        et.setLayoutParams(lp);
        container.addView(et);
        return et;
    }

    private void salvarPerfil(int id, String nome, String descricao) {
        showLoading("Salvando...");
        new Thread(() -> {
            try {
                if (id == 0) {
                    int novoId = pm.criarPerfil(nome, descricao);
                    Log.d(TAG, "Perfil criado com ID: " + novoId);
                } else {
                    pm.atualizarPerfil(id, nome, descricao);
                }
                hideLoading();
                showToast("Perfil salvo com sucesso!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void excluirPerfil(int id) {
        showLoading("Excluindo...");
        new Thread(() -> {
            try {
                pm.excluirPerfil(id);
                hideLoading();
                showToast("Perfil excluido!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }

    // =========================================================================
    // DIALOG DE PERMISSOES DO PERFIL
    // =========================================================================

    private void showPermissoesDialog(Perfil perfil) {
        showLoading("Carregando permissoes...");
        new Thread(() -> {
            try {
                List<Permissao> permissoes = pm.getPermissoesDoPerfil(perfil.getId());
                Map<String, List<Permissao>> porModulo = new LinkedHashMap<>();
                for (Permissao p : permissoes) {
                    String modulo = p.getModulo();
                    if (!porModulo.containsKey(modulo)) {
                        porModulo.put(modulo, new ArrayList<>());
                    }
                    porModulo.get(modulo).add(p);
                }

                hideLoading();
                runOnUiThread(() -> {
                    try {
                        if (isFinishing() || isDestroyed()) return;
                        showPermissoesDialogUI(perfil, permissoes, porModulo);
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao exibir dialog de permissoes", e);
                        showError("Erro ao exibir permissoes: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                hideLoading();
                Log.e(TAG, "Erro ao carregar permissoes do perfil", e);
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private void showPermissoesDialogUI(Perfil perfil, List<Permissao> permissoes,
                                         Map<String, List<Permissao>> porModulo) {
        try {
            if (isFinishing() || isDestroyed()) return;

            ScrollView scrollView = new ScrollView(this);
            scrollView.setBackgroundColor(Color.parseColor("#0A0E27"));
            scrollView.setPadding(24, 16, 24, 16);

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            // Info do perfil
            TextView tvInfo = new TextView(this);
            tvInfo.setText("Perfil: " + perfil.getNome() +
                    (perfil.isSistematico() ? " (Sistema)" : ""));
            tvInfo.setTextColor(Color.parseColor("#00BCD4"));
            tvInfo.setTextSize(16);
            tvInfo.setTypeface(null, Typeface.BOLD);
            tvInfo.setPadding(0, 0, 0, 16);
            mainLayout.addView(tvInfo);

            if (!perfil.isSistematico()) {
                // ===== Perfil editavel =====

                EditText etBuscaPermissao = criarBuscaPermissoes(mainLayout);

                // Botoes de atalho globais
                LinearLayout btnLayout = new LinearLayout(this);
                btnLayout.setOrientation(LinearLayout.HORIZONTAL);
                btnLayout.setGravity(Gravity.CENTER);
                btnLayout.setPadding(0, 0, 0, 16);

                Button btnMarcarTodos = criarBotaoAtalho("Marcar Todos", "#00E676");
                Button btnDesmarcarTodos = criarBotaoAtalho("Desmarcar Todos", "#FF5252");

                btnLayout.addView(btnMarcarTodos);
                btnLayout.addView(btnDesmarcarTodos);
                mainLayout.addView(btnLayout);

                List<CheckBox> todosCheckboxes = new ArrayList<>();
                Map<String, List<CheckBox>> checkboxesPorModulo = new LinkedHashMap<>();

                for (Map.Entry<String, List<Permissao>> entry : porModulo.entrySet()) {
                    if (isFinishing() || isDestroyed()) return;

                    String moduloNome = entry.getKey();
                    List<CheckBox> moduloCheckboxes = new ArrayList<>();

                    // Header do modulo com botao de toggle
                    LinearLayout headerLayout = new LinearLayout(this);
                    headerLayout.setOrientation(LinearLayout.HORIZONTAL);
                    headerLayout.setGravity(Gravity.CENTER_VERTICAL);
                    headerLayout.setPadding(0, 20, 0, 8);

                    TextView tvModulo = new TextView(this);
                    tvModulo.setText("\u25B6 " + moduloNome);
                    tvModulo.setTextColor(Color.parseColor("#FFD700"));
                    tvModulo.setTextSize(15);
                    tvModulo.setTypeface(null, Typeface.BOLD);
                    tvModulo.setLayoutParams(new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    headerLayout.addView(tvModulo);

                    // Botao toggle do modulo
                    Button btnToggleModulo = new Button(this);
                    btnToggleModulo.setText("Todos");
                    btnToggleModulo.setTextSize(10);
                    btnToggleModulo.setTextColor(Color.WHITE);
                    btnToggleModulo.setAllCaps(false);
                    GradientDrawable toggleBg = new GradientDrawable();
                    toggleBg.setShape(GradientDrawable.RECTANGLE);
                    toggleBg.setCornerRadius(12f);
                    toggleBg.setColor(Color.parseColor("#455A64"));
                    btnToggleModulo.setBackground(toggleBg);
                    btnToggleModulo.setPadding(16, 4, 16, 4);
                    LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    toggleLp.setMargins(8, 0, 0, 0);
                    btnToggleModulo.setLayoutParams(toggleLp);
                    headerLayout.addView(btnToggleModulo);

                    mainLayout.addView(headerLayout);

                    // Separador
                    View divider = new View(this);
                    divider.setBackgroundColor(Color.parseColor("#2A2F5A"));
                    divider.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 2));
                    mainLayout.addView(divider);

                    // Checkboxes
                    for (Permissao perm : entry.getValue()) {
                        CheckBox cb = new CheckBox(this);
                        cb.setText(perm.getAcao() + " - " + perm.getDescricao());
                        cb.setTextColor(Color.parseColor("#B0BEC5"));
                        cb.setTextSize(13);
                        cb.setChecked(perm.isConcedida());
                        cb.setTag(perm);
                        cb.setPadding(8, 4, 8, 4);

                        try {
                            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(
                                    Color.parseColor("#00BCD4")));
                        } catch (Exception ignored) {}

                        mainLayout.addView(cb);
                        todosCheckboxes.add(cb);
                        moduloCheckboxes.add(cb);
                    }

                    checkboxesPorModulo.put(moduloNome, moduloCheckboxes);

                    // Toggle do modulo
                    final List<CheckBox> finalModuloCbs = moduloCheckboxes;
                    btnToggleModulo.setOnClickListener(v -> {
                        boolean algumDesmarcado = false;
                        for (CheckBox cb : finalModuloCbs) {
                            if (!cb.isChecked()) { algumDesmarcado = true; break; }
                        }
                        for (CheckBox cb : finalModuloCbs) cb.setChecked(algumDesmarcado);
                    });
                }

                configurarBuscaPermissoes(etBuscaPermissao, todosCheckboxes);

                // Acao dos botoes globais
                btnMarcarTodos.setOnClickListener(v -> {
                    for (CheckBox cb : todosCheckboxes) cb.setChecked(true);
                });
                btnDesmarcarTodos.setOnClickListener(v -> {
                    for (CheckBox cb : todosCheckboxes) cb.setChecked(false);
                });

                scrollView.addView(mainLayout);

                if (isFinishing() || isDestroyed()) return;

                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle("\uD83D\uDD11 Permissoes: " + perfil.getNome())
                        .setView(scrollView)
                        .setPositiveButton("Salvar", (d, w) -> {
                            List<String> chavesPermitidas = new ArrayList<>();
                            for (CheckBox cb : todosCheckboxes) {
                                if (cb.isChecked()) {
                                    Permissao p = (Permissao) cb.getTag();
                                    if (p != null) {
                                        chavesPermitidas.add(p.getChave());
                                    }
                                }
                            }
                            salvarPermissoes(perfil.getId(), chavesPermitidas);
                        })
                        .setNegativeButton("Cancelar", null)
                        .setCancelable(true)
                        .create();

                dialog.show();
                try {
                    if (dialog.getWindow() != null) {
                        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
                        dialog.getWindow().setLayout(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                Math.min(maxHeight, LinearLayout.LayoutParams.WRAP_CONTENT));
                    }
                } catch (Exception ignored) {}

            } else {
                // ===== Perfil sistematico - apenas visualizacao =====
                EditText etBuscaPermissao = criarBuscaPermissoes(mainLayout);
                List<TextView> textosPermissoes = new ArrayList<>();
                for (Map.Entry<String, List<Permissao>> entry : porModulo.entrySet()) {
                    if (isFinishing() || isDestroyed()) return;

                    TextView tvModulo = new TextView(this);
                    tvModulo.setText("\u25B6 " + entry.getKey());
                    tvModulo.setTextColor(Color.parseColor("#FFD700"));
                    tvModulo.setTextSize(15);
                    tvModulo.setTypeface(null, Typeface.BOLD);
                    tvModulo.setPadding(0, 20, 0, 8);
                    mainLayout.addView(tvModulo);

                    for (Permissao perm : entry.getValue()) {
                        TextView tvPerm = new TextView(this);
                        tvPerm.setText("\u2705 " + perm.getAcao() + " - " + perm.getDescricao());
                        tvPerm.setTextColor(Color.parseColor("#00E676"));
                        tvPerm.setTextSize(13);
                        tvPerm.setPadding(24, 4, 8, 4);
                        tvPerm.setTag(perm);
                        mainLayout.addView(tvPerm);
                        textosPermissoes.add(tvPerm);
                    }
                }

                configurarBuscaPermissoes(etBuscaPermissao, textosPermissoes);

                scrollView.addView(mainLayout);

                if (isFinishing() || isDestroyed()) return;

                new AlertDialog.Builder(this)
                        .setTitle("\uD83D\uDD12 Permissoes: " + perfil.getNome())
                        .setView(scrollView)
                        .setPositiveButton("Fechar", null)
                        .setCancelable(true)
                        .show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro critico ao construir dialog de permissoes", e);
            try {
                showError("Erro ao exibir permissoes.\nTente novamente.");
            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // DIALOG DE PERMISSOES POR USUARIO (OVERRIDES)
    // =========================================================================

    /**
     * v6.0.0 - Dialog avancado para gerenciar permissoes individuais de um usuario.
     * 
     * Exibe checkboxes com 3 estados visuais:
     * - Checkbox marcado (verde) = permissao concedida (do perfil ou override adicionar)
     * - Checkbox desmarcado (cinza) = permissao negada (nao tem no perfil ou override remover)
     * - Indicador de override: texto em azul (adicionada) ou vermelho (removida)
     */
    private void showPermissoesUsuarioDialog(int userId, String nomeUsuario, String perfilNome) {
        showLoading("Carregando permissoes do usuario...");
        new Thread(() -> {
            try {
                // Carregar permissoes efetivas
                List<Permissao> permissoes = pm.getPermissoesEfetivasDoUsuario(userId);

                // Carregar overrides atuais
                Map<String, String> overrides = pm.getOverridesDoUsuario(userId);

                // Carregar permissoes do perfil para comparacao
                int perfilId = 0;
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT perfil_id FROM usuarios WHERE id = ?");
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) perfilId = rs.getInt("perfil_id");
                rs.close();
                ps.close();

                Set<String> permissoesPerfil = new HashSet<>();
                if (perfilId > 0) {
                    PreparedStatement psP = conn.prepareStatement(
                            "SELECT pe.chave FROM perfil_permissoes pp " +
                            "JOIN permissoes pe ON pp.permissao_id = pe.id WHERE pp.perfil_id = ?");
                    psP.setInt(1, perfilId);
                    ResultSet rsP = psP.executeQuery();
                    while (rsP.next()) permissoesPerfil.add(rsP.getString("chave"));
                    rsP.close();
                    psP.close();
                }

                // Agrupar por modulo
                Map<String, List<Permissao>> porModulo = new LinkedHashMap<>();
                for (Permissao p : permissoes) {
                    String modulo = p.getModulo();
                    if (!porModulo.containsKey(modulo)) {
                        porModulo.put(modulo, new ArrayList<>());
                    }
                    porModulo.get(modulo).add(p);
                }

                final Set<String> finalPermissoesPerfil = permissoesPerfil;
                final Map<String, String> finalOverrides = overrides;

                hideLoading();
                runOnUiThread(() -> {
                    try {
                        if (isFinishing() || isDestroyed()) return;
                        showPermissoesUsuarioDialogUI(userId, nomeUsuario, perfilNome,
                                permissoes, porModulo, finalPermissoesPerfil, finalOverrides);
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao exibir dialog de permissoes do usuario", e);
                        showError("Erro ao exibir permissoes: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private void showPermissoesUsuarioDialogUI(int userId, String nomeUsuario, String perfilNome,
                                                 List<Permissao> permissoes,
                                                 Map<String, List<Permissao>> porModulo,
                                                 Set<String> permissoesPerfil,
                                                 Map<String, String> overridesAtuais) {
        try {
            if (isFinishing() || isDestroyed()) return;

            ScrollView scrollView = new ScrollView(this);
            scrollView.setBackgroundColor(Color.parseColor("#0A0E27"));
            scrollView.setPadding(24, 16, 24, 16);

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            // Info do usuario
            TextView tvInfo = new TextView(this);
            tvInfo.setText("Usuario: " + nomeUsuario + "\nPerfil base: " + perfilNome);
            tvInfo.setTextColor(Color.parseColor("#00BCD4"));
            tvInfo.setTextSize(14);
            tvInfo.setTypeface(null, Typeface.BOLD);
            tvInfo.setPadding(0, 0, 0, 8);
            mainLayout.addView(tvInfo);

            // Legenda
            TextView tvLegenda = new TextView(this);
            tvLegenda.setText("\u25CF Herdado do perfil   \u25CF Adicionado   \u25CF Removido");
            tvLegenda.setTextColor(Color.parseColor("#90A4AE"));
            tvLegenda.setTextSize(11);
            tvLegenda.setPadding(0, 0, 0, 16);
            mainLayout.addView(tvLegenda);

            EditText etBuscaPermissao = criarBuscaPermissoes(mainLayout);

            // Botoes de atalho
            LinearLayout btnLayout = new LinearLayout(this);
            btnLayout.setOrientation(LinearLayout.HORIZONTAL);
            btnLayout.setGravity(Gravity.CENTER);
            btnLayout.setPadding(0, 0, 0, 8);

            Button btnResetPerfil = criarBotaoAtalho("Resetar p/ Perfil", "#FF9800");
            Button btnMarcarTodos = criarBotaoAtalho("Marcar Todos", "#00E676");
            Button btnDesmarcarTodos = criarBotaoAtalho("Desmarcar Todos", "#FF5252");

            btnLayout.addView(btnResetPerfil);
            btnLayout.addView(btnMarcarTodos);
            btnLayout.addView(btnDesmarcarTodos);
            mainLayout.addView(btnLayout);

            List<CheckBox> todosCheckboxes = new ArrayList<>();

            for (Map.Entry<String, List<Permissao>> entry : porModulo.entrySet()) {
                if (isFinishing() || isDestroyed()) return;

                String moduloNome = entry.getKey();

                // Header do modulo
                LinearLayout headerLayout = new LinearLayout(this);
                headerLayout.setOrientation(LinearLayout.HORIZONTAL);
                headerLayout.setGravity(Gravity.CENTER_VERTICAL);
                headerLayout.setPadding(0, 20, 0, 8);

                TextView tvModulo = new TextView(this);
                tvModulo.setText("\u25B6 " + moduloNome);
                tvModulo.setTextColor(Color.parseColor("#FFD700"));
                tvModulo.setTextSize(15);
                tvModulo.setTypeface(null, Typeface.BOLD);
                tvModulo.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                headerLayout.addView(tvModulo);

                mainLayout.addView(headerLayout);

                View divider = new View(this);
                divider.setBackgroundColor(Color.parseColor("#2A2F5A"));
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 2));
                mainLayout.addView(divider);

                for (Permissao perm : entry.getValue()) {
                    String chave = perm.getChave();
                    boolean doPerfil = permissoesPerfil.contains(chave);
                    String override = overridesAtuais.get(chave);

                    CheckBox cb = new CheckBox(this);
                    String label = perm.getAcao() + " - " + perm.getDescricao();

                    // Indicador visual de override
                    if (override != null) {
                        if ("adicionar".equals(override)) {
                            label += "  [+ADD]";
                            cb.setTextColor(Color.parseColor("#00BCD4")); // Azul = adicionado
                        } else if ("remover".equals(override)) {
                            label += "  [-REM]";
                            cb.setTextColor(Color.parseColor("#FF5252")); // Vermelho = removido
                        }
                    } else if (doPerfil) {
                        cb.setTextColor(Color.parseColor("#B0BEC5")); // Cinza = herdado
                    } else {
                        cb.setTextColor(Color.parseColor("#546E7A")); // Cinza escuro = nao tem
                    }

                    cb.setText(label);
                    cb.setTextSize(13);
                    cb.setChecked(perm.isConcedida());
                    cb.setTag(perm);
                    cb.setPadding(8, 4, 8, 4);

                    // Listener para atualizar cor ao mudar estado
                    cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        boolean perfHas = permissoesPerfil.contains(chave);
                        if (isChecked && !perfHas) {
                            // Adicionando algo que o perfil nao tem
                            cb.setTextColor(Color.parseColor("#00BCD4"));
                        } else if (!isChecked && perfHas) {
                            // Removendo algo que o perfil tem
                            cb.setTextColor(Color.parseColor("#FF5252"));
                        } else {
                            // Igual ao perfil (sem override)
                            cb.setTextColor(Color.parseColor("#B0BEC5"));
                        }
                    });

                    try {
                        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(
                                Color.parseColor("#00BCD4")));
                    } catch (Exception ignored) {}

                    mainLayout.addView(cb);
                    todosCheckboxes.add(cb);
                }
            }

            configurarBuscaPermissoes(etBuscaPermissao, todosCheckboxes);

            // Acoes dos botoes
            btnResetPerfil.setOnClickListener(v -> {
                for (CheckBox cb : todosCheckboxes) {
                    Permissao p = (Permissao) cb.getTag();
                    if (p != null) {
                        cb.setChecked(permissoesPerfil.contains(p.getChave()));
                    }
                }
            });
            btnMarcarTodos.setOnClickListener(v -> {
                for (CheckBox cb : todosCheckboxes) cb.setChecked(true);
            });
            btnDesmarcarTodos.setOnClickListener(v -> {
                for (CheckBox cb : todosCheckboxes) cb.setChecked(false);
            });

            scrollView.addView(mainLayout);

            if (isFinishing() || isDestroyed()) return;

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("\uD83D\uDC64 Permissoes: " + nomeUsuario)
                    .setView(scrollView)
                    .setPositiveButton("Salvar", (d, w) -> {
                        // Calcular overrides: diferencas entre estado atual e perfil
                        List<String> adicionadas = new ArrayList<>();
                        List<String> removidas = new ArrayList<>();

                        for (CheckBox cb : todosCheckboxes) {
                            Permissao p = (Permissao) cb.getTag();
                            if (p == null) continue;
                            String chave = p.getChave();
                            boolean marcado = cb.isChecked();
                            boolean doPerfil = permissoesPerfil.contains(chave);

                            if (marcado && !doPerfil) {
                                adicionadas.add(chave); // Adicionar: marcado mas perfil nao tem
                            } else if (!marcado && doPerfil) {
                                removidas.add(chave); // Remover: desmarcado mas perfil tem
                            }
                            // Se marcado == doPerfil, nao precisa de override
                        }

                        salvarOverridesUsuario(userId, nomeUsuario, adicionadas, removidas);
                    })
                    .setNegativeButton("Cancelar", null)
                    .setCancelable(true)
                    .create();

            dialog.show();
            try {
                if (dialog.getWindow() != null) {
                    int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
                    dialog.getWindow().setLayout(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            Math.min(maxHeight, LinearLayout.LayoutParams.WRAP_CONTENT));
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            Log.e(TAG, "Erro critico ao construir dialog de permissoes do usuario", e);
            try {
                showError("Erro ao exibir permissoes.\nTente novamente.");
            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // SALVAR PERMISSOES
    // =========================================================================

    private EditText criarBuscaPermissoes(LinearLayout parent) {
        EditText busca = new EditText(this);
        busca.setHint("Digite o nome da permissao...");
        busca.setSingleLine(true);
        busca.setTextColor(Color.WHITE);
        busca.setHintTextColor(Color.parseColor("#78909C"));
        busca.setBackgroundResource(R.drawable.input_bg);
        busca.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 4, 0, 14);
        busca.setLayoutParams(lp);
        parent.addView(busca);
        return busca;
    }

    /** Filtra as permissoes enquanto o usuario digita, incluindo acao, descricao, modulo e chave. */
    private void configurarBuscaPermissoes(EditText busca, List<? extends TextView> itens) {
        busca.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String termo = normalizarBuscaPermissao(s == null ? "" : s.toString());
                for (TextView item : itens) {
                    StringBuilder pesquisavel = new StringBuilder(String.valueOf(item.getText()));
                    Object tag = item.getTag();
                    if (tag instanceof Permissao) {
                        Permissao permissao = (Permissao) tag;
                        pesquisavel.append(' ').append(permissao.getModulo())
                                .append(' ').append(permissao.getAcao())
                                .append(' ').append(permissao.getDescricao())
                                .append(' ').append(permissao.getChave());
                    }
                    boolean exibir = termo.isEmpty()
                            || normalizarBuscaPermissao(pesquisavel.toString()).contains(termo);
                    item.setVisibility(exibir ? View.VISIBLE : View.GONE);
                }
            }

            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private static String normalizarBuscaPermissao(String valor) {
        return Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private void salvarPermissoes(int perfilId, List<String> chavesPermitidas) {
        showLoading("Salvando permissoes...");
        new Thread(() -> {
            try {
                pm.salvarPermissoesDoPerfil(perfilId, chavesPermitidas);
                hideLoading();
                showSuccess("Permissoes salvas com sucesso!\n\n"
                        + chavesPermitidas.size() + " permissao(oes) concedida(s).");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void salvarOverridesUsuario(int userId, String nomeUsuario,
                                         List<String> adicionadas, List<String> removidas) {
        showLoading("Salvando permissoes do usuario...");
        new Thread(() -> {
            try {
                pm.salvarOverridesDoUsuario(userId, adicionadas, removidas);
                hideLoading();
                int total = adicionadas.size() + removidas.size();
                String msg = "Permissoes de \"" + nomeUsuario + "\" salvas!\n\n";
                if (total == 0) {
                    msg += "Nenhum override individual. Usuario segue as permissoes do perfil.";
                } else {
                    msg += adicionadas.size() + " permissao(oes) adicionada(s)\n"
                            + removidas.size() + " permissao(oes) removida(s)";
                }
                showSuccess(msg);
                if (mostraUsuarios) loadUsuarios();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void limparOverridesUsuario(int userId, String nomeUsuario) {
        showLoading("Limpando overrides...");
        new Thread(() -> {
            try {
                pm.limparOverridesDoUsuario(userId);
                hideLoading();
                showSuccess("Overrides de \"" + nomeUsuario + "\" removidos!\n\n"
                        + "O usuario agora segue apenas as permissoes do perfil.");
                if (mostraUsuarios) loadUsuarios();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }

    // =========================================================================
    // DIALOG DE PERSONALIZACAO DO DASHBOARD (PERFIL PERSONALIZAVEL)
    // =========================================================================

    /**
     * v7.0.1 - Dialog especial para o perfil "Personalizavel".
     * Permite ao administrador escolher quais botoes do dashboard (area principal)
     * o perfil podera usar. Ao marcar um botao, automaticamente concede tanto
     * a permissao de dashboard (visibilidade) quanto a permissao de modulo (acesso).
     */
    private void showPersonalizarDashboardDialog(Perfil perfil) {
        showLoading("Carregando configuracao...");
        new Thread(() -> {
            try {
                List<Permissao> permissoesAtuais = pm.getPermissoesDoPerfil(perfil.getId());
                Set<String> concedidas = new HashSet<>();
                for (Permissao p : permissoesAtuais) {
                    if (p.isConcedida()) {
                        concedidas.add(p.getChave());
                    }
                }

                List<String[]> dashboardBotoes = PermissionConstants.getDashboardBotoes();

                hideLoading();
                runOnUiThread(() -> {
                    try {
                        if (isFinishing() || isDestroyed()) return;
                        showPersonalizarDashboardDialogUI(perfil, dashboardBotoes, concedidas);
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao exibir dialog de personalizacao", e);
                        showError("Erro ao exibir personalizacao: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private void showPersonalizarDashboardDialogUI(Perfil perfil, List<String[]> dashboardBotoes,
                                                     Set<String> concedidas) {
        try {
            if (isFinishing() || isDestroyed()) return;

            ScrollView scrollView = new ScrollView(this);
            scrollView.setBackgroundColor(Color.parseColor("#0A0E27"));
            scrollView.setPadding(24, 16, 24, 16);

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            // Info
            TextView tvInfo = new TextView(this);
            tvInfo.setText("Perfil: " + perfil.getNome() + "\n\n" +
                    "Selecione quais botoes da area principal (Dashboard) " +
                    "este perfil podera visualizar e acessar.\n\n" +
                    "Ao marcar um botao, o acesso ao modulo correspondente " +
                    "tambem sera concedido automaticamente.");
            tvInfo.setTextColor(Color.parseColor("#00BCD4"));
            tvInfo.setTextSize(14);
            tvInfo.setPadding(0, 0, 0, 16);
            mainLayout.addView(tvInfo);

            // Botoes de atalho
            LinearLayout btnLayout = new LinearLayout(this);
            btnLayout.setOrientation(LinearLayout.HORIZONTAL);
            btnLayout.setGravity(Gravity.CENTER);
            btnLayout.setPadding(0, 0, 0, 16);

            Button btnMarcarTodos = criarBotaoAtalho("Marcar Todos", "#00E676");
            Button btnDesmarcarTodos = criarBotaoAtalho("Desmarcar Todos", "#FF5252");

            btnLayout.addView(btnMarcarTodos);
            btnLayout.addView(btnDesmarcarTodos);
            mainLayout.addView(btnLayout);

            // Separador
            View divider = new View(this);
            divider.setBackgroundColor(Color.parseColor("#2A2F5A"));
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 2));
            mainLayout.addView(divider);

            // Header
            TextView tvHeader = new TextView(this);
            tvHeader.setText("\u25B6 Botoes do Dashboard");
            tvHeader.setTextColor(Color.parseColor("#FFD700"));
            tvHeader.setTextSize(15);
            tvHeader.setTypeface(null, Typeface.BOLD);
            tvHeader.setPadding(0, 16, 0, 12);
            mainLayout.addView(tvHeader);

            // Checkboxes para cada botao do dashboard
            List<CheckBox> todosCheckboxes = new ArrayList<>();
            Set<String> processados = new HashSet<>();

            for (String[] botao : dashboardBotoes) {
                String nomeAmigavel = botao[0];
                String chaveDashboard = botao[1];
                String chaveModulo = botao[2];

                // Evitar duplicatas (ex: Cardapio QR Code usa mesma chave de Gerenciar Mesas)
                if (processados.contains(chaveDashboard)) continue;
                processados.add(chaveDashboard);

                CheckBox cb = new CheckBox(this);
                cb.setText(nomeAmigavel);
                cb.setTextColor(Color.parseColor("#B0BEC5"));
                cb.setTextSize(14);
                cb.setChecked(concedidas.contains(chaveDashboard));
                cb.setTag(new String[]{chaveDashboard, chaveModulo});
                cb.setPadding(8, 8, 8, 8);

                try {
                    cb.setButtonTintList(android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#00BCD4")));
                } catch (Exception ignored) {}

                mainLayout.addView(cb);
                todosCheckboxes.add(cb);
            }

            // Acoes dos botoes globais
            btnMarcarTodos.setOnClickListener(v -> {
                for (CheckBox cb : todosCheckboxes) cb.setChecked(true);
            });
            btnDesmarcarTodos.setOnClickListener(v -> {
                for (CheckBox cb : todosCheckboxes) cb.setChecked(false);
            });

            scrollView.addView(mainLayout);

            if (isFinishing() || isDestroyed()) return;

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("\uD83D\uDD27 Personalizar: " + perfil.getNome())
                    .setView(scrollView)
                    .setPositiveButton("Salvar", (d, w) -> {
                        // Coletar permissoes selecionadas
                        List<String> chavesPermitidas = new ArrayList<>();
                        // Sempre incluir Trocar Senha
                        chavesPermitidas.add(PermissionConstants.TROCAR_SENHA);
                        chavesPermitidas.add(PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA);

                        for (CheckBox cb : todosCheckboxes) {
                            if (cb.isChecked()) {
                                String[] tags = (String[]) cb.getTag();
                                if (tags != null) {
                                    // Adicionar permissao de dashboard
                                    if (tags[0] != null) chavesPermitidas.add(tags[0]);
                                    // Adicionar permissao de modulo
                                    if (tags[1] != null) chavesPermitidas.add(tags[1]);
                                }
                            }
                        }

                        salvarPermissoes(perfil.getId(), chavesPermitidas);
                    })
                    .setNegativeButton("Cancelar", null)
                    .setCancelable(true)
                    .create();

            dialog.show();
            try {
                if (dialog.getWindow() != null) {
                    int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
                    dialog.getWindow().setLayout(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            Math.min(maxHeight, LinearLayout.LayoutParams.WRAP_CONTENT));
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            Log.e(TAG, "Erro critico ao construir dialog de personalizacao", e);
            try {
                showError("Erro ao exibir personalizacao.\nTente novamente.");
            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // UTILITARIOS
    // =========================================================================

    private Button criarBotaoAtalho(String texto, String cor) {
        Button btn = new Button(this);
        btn.setText(texto);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(10);
        btn.setAllCaps(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(16f);
        bg.setColor(Color.parseColor(cor));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(lp);
        btn.setPadding(12, 8, 12, 8);

        return btn;
    }
}
