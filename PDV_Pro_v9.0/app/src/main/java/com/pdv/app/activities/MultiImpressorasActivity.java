package com.pdv.app.activities;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.MultiPrinterManager;
import com.pdv.app.utils.PrinterManager;
import com.pdv.app.utils.ThermalPrinterDriver;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tela profissional de Impressao em Multiimpressoras.
 * Permite vincular categoria/tipo de produto a uma impressora especifica.
 */
public class MultiImpressorasActivity extends BaseActivity {
    private LinearLayout listaLayout;
    private CheckBox cbAtivar;
    private MultiPrinterManager manager;
    private List<Categoria> categorias = new ArrayList<>();

    private final String[] tipos = new String[] {
            PrinterManager.TIPO_REDE,
            PrinterManager.TIPO_REDE_RAW,
            PrinterManager.TIPO_BLUETOOTH,
            PrinterManager.TIPO_USB,
            PrinterManager.TIPO_SMB,
            PrinterManager.TIPO_SMB_DIRETO,
            PrinterManager.TIPO_BT_WINDOWS,
            PrinterManager.TIPO_PRINT_SERVER
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.MULTIIMPRESSORAS_ACESSAR)) { return; }
        manager = new MultiPrinterManager(this);
        categorias = new ArrayList<>();
        montarTela();
        mostrarCarregando();
        carregarCategoriasAsync();
    }

    /**
     * Carrega categorias fora da thread principal para evitar travamento/ANR ao abrir a tela.
     * Bancos antigos, grandes ou bloqueados podem demorar; por isso a tela abre primeiro
     * e a lista aparece assim que o carregamento terminar.
     */
    private void carregarCategoriasAsync() {
        new Thread(() -> {
            List<Categoria> carregadas = carregarCategoriasSeguro();
            runOnUiThread(() -> {
                categorias = carregadas;
                recarregarLista();
                if (categorias.isEmpty()) {
                    showToast("Nenhuma categoria/tipo de produto encontrada.");
                }
            });
        }).start();
    }

    private void mostrarCarregando() {
        if (listaLayout == null) return;
        listaLayout.removeAllViews();
        TextView carregando = texto("Carregando configuracao de multiimpressoras...");
        carregando.setGravity(Gravity.CENTER);
        carregando.setPadding(0, dp(26), 0, dp(26));
        listaLayout.addView(carregando);
    }

    private void montarTela() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(18));
        root.setBackgroundColor(Color.rgb(8, 18, 32));
        scroll.addView(root);

        TextView titulo = titulo("Impressao em Multiimpressoras", 22);
        root.addView(titulo);

        TextView sub = texto("Direcione automaticamente os produtos de cada categoria/tipo para uma impressora especifica. Exemplo: bebidas no bar, lanches na cozinha e pizzas no forno.");
        root.addView(sub);

        cbAtivar = new CheckBox(this);
        cbAtivar.setText("Ativar impressao em multiimpressoras");
        cbAtivar.setTextColor(Color.WHITE);
        cbAtivar.setTextSize(16);
        cbAtivar.setPadding(0, dp(12), 0, dp(12));
        cbAtivar.setChecked(manager.isEnabled());
        cbAtivar.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            private boolean interno = false;
            @Override public void onCheckedChanged(android.widget.CompoundButton v, boolean checked) {
                if (interno) return;
                if (!PermissionHelper.verificar(MultiImpressorasActivity.this, PermissionConstants.MULTIIMPRESSORAS_ATIVAR)) {
                    interno = true;
                    cbAtivar.setChecked(!checked);
                    interno = false;
                    return;
                }
                new Thread(() -> manager.setEnabled(checked)).start();
            }
        });
        root.addView(cbAtivar);

        LinearLayout botoes = new LinearLayout(this);
        botoes.setOrientation(LinearLayout.HORIZONTAL);
        botoes.setGravity(Gravity.CENTER);
        botoes.setPadding(0, dp(8), 0, dp(12));
        root.addView(botoes);

        Button btnAdd = botao("+ ADICIONAR CATEGORIA");
        btnAdd.setOnClickListener(v -> { if (PermissionHelper.verificar(this, PermissionConstants.MULTIIMPRESSORAS_CRIAR_REGRA)) abrirDialogRegra(null); });
        botoes.addView(btnAdd, new LinearLayout.LayoutParams(0, dp(58), 1));

        Button btnReload = botao("ATUALIZAR");
        btnReload.setOnClickListener(v -> recarregarLista());
        LinearLayout.LayoutParams reloadParams = new LinearLayout.LayoutParams(0, dp(58), 1);
        reloadParams.setMargins(dp(8), 0, 0, 0);
        botoes.addView(btnReload, reloadParams);

        listaLayout = new LinearLayout(this);
        listaLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(listaLayout);

        TextView rodape = texto("Observacao: a impressao principal do cupom continua funcionando normalmente. Com esta funcao ativada, o sistema tambem separa e envia os produtos para as impressoras configuradas por categoria.");
        rodape.setPadding(0, dp(12), 0, 0);
        root.addView(rodape);

        setContentView(scroll);
    }

    private void recarregarLista() {
        listaLayout.removeAllViews();
        List<MultiPrinterManager.Rule> rules = manager.getRules();
        if (rules.isEmpty()) {
            TextView vazio = texto("Nenhuma categoria configurada. Toque em ADICIONAR CATEGORIA para criar a primeira regra.");
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(0, dp(26), 0, dp(26));
            listaLayout.addView(vazio);
            return;
        }
        for (MultiPrinterManager.Rule r : rules) {
            listaLayout.addView(cardRegra(r));
        }
    }

    private View cardRegra(MultiPrinterManager.Rule r) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackgroundColor(Color.rgb(15, 35, 58));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, dp(8), 0, dp(8));
        card.setLayoutParams(cp);

        TextView t = titulo((r.ativo ? "🟢 " : "⚪ ") + r.categoriaNome, 18);
        card.addView(t);
        card.addView(texto("Impressora: " + nvl(r.nomeImpressora, "Sem nome")));
        card.addView(texto("Tipo: " + r.tipo + " | IP: " + nvl(r.ip, "-") + " | Porta: " + r.porta + " | MAC/BT: " + nvl(r.mac, "-")));
        card.addView(texto("Driver/modelo: " + ThermalPrinterDriver.getProfile(r.driverId).name));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        card.addView(row);

        Button edit = botao("EDITAR");
        edit.setOnClickListener(v -> { if (PermissionHelper.verificar(this, PermissionConstants.MULTIIMPRESSORAS_EDITAR_REGRA)) abrirDialogRegra(r); });
        row.addView(edit, new LinearLayout.LayoutParams(0, dp(52), 1));

        Button test = botao("TESTAR");
        test.setOnClickListener(v -> { if (!PermissionHelper.verificar(this, PermissionConstants.MULTIIMPRESSORAS_TESTAR_IMPRESSORA)) return; new Thread(() -> {
            boolean ok = manager.testarRegra(r);
            runOnUiThread(() -> showToast(ok ? "Teste enviado para a impressora." : "Falha ao enviar teste."));
        }).start(); });
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(52), 1);
        p2.setMargins(dp(8), 0, 0, 0);
        row.addView(test, p2);

        Button del = botao("REMOVER");
        del.setOnClickListener(v -> { if (!PermissionHelper.verificar(this, PermissionConstants.MULTIIMPRESSORAS_EXCLUIR_REGRA)) return; new AlertDialog.Builder(this)
                .setTitle("Remover regra")
                .setMessage("Deseja remover a regra da categoria " + r.categoriaNome + "?")
                .setPositiveButton("Remover", (d, w) -> { manager.removeRule(r.categoriaId); recarregarLista(); })
                .setNegativeButton("Cancelar", null)
                .show(); });
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, dp(52), 1);
        p3.setMargins(dp(8), 0, 0, 0);
        row.addView(del, p3);
        return card;
    }

    private void abrirDialogRegra(MultiPrinterManager.Rule editar) {
        if (categorias.isEmpty()) {
            showError("Nenhuma categoria/tipo de produto cadastrado. Cadastre primeiro em Tipos de Produto.");
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), 0);

        CheckBox ativo = new CheckBox(this);
        ativo.setText("Regra ativa");
        ativo.setChecked(editar == null || editar.ativo);
        box.addView(ativo);

        Spinner spCategoria = new Spinner(this);
        ArrayAdapter<Categoria> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categorias);
        spCategoria.setAdapter(catAdapter);
        box.addView(label("Categoria/tipo de produto"));
        box.addView(spCategoria);

        EditText nomeImp = campo("Nome da impressora. Ex: Cozinha, Bar, Forno");
        box.addView(label("Nome de identificacao"));
        box.addView(nomeImp);

        final ArrayList<PrinterOption> opcoesImpressora = carregarOpcoesImpressoras(editar);
        Spinner spImpressora = new Spinner(this);
        ArrayAdapter<PrinterOption> impAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, opcoesImpressora);
        spImpressora.setAdapter(impAdapter);
        box.addView(label("Escolher impressora instalada ou Bluetooth pareada"));
        box.addView(spImpressora);

        Button btnBuscarInstaladas = botao("BUSCAR IMPRESSORAS INSTALADAS DO PC/SERVIDOR");
        box.addView(btnBuscarInstaladas, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        TextView dicaImpressora = texto("Escolha uma impressora pareada Bluetooth, uma impressora instalada no PC pelo Print Server, a impressora comum do sistema, ou mantenha 'Digitar manualmente'. Os campos abaixo continuam disponíveis para ajuste fino.");
        box.addView(dicaImpressora);

        Spinner spTipo = new Spinner(this);
        ArrayAdapter<String> tipoAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, tipos);
        spTipo.setAdapter(tipoAdapter);
        box.addView(label("Tipo de impressora"));
        box.addView(spTipo);

        EditText ip = campo("IP ou caminho. Ex: 192.168.0.50");
        box.addView(label("IP / Caminho SMB / Host"));
        box.addView(ip);

        EditText porta = campo("Porta. Ex: 9100");
        porta.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        box.addView(label("Porta"));
        box.addView(porta);

        EditText mac = campo("MAC Bluetooth ou COM Windows. Ex: 00:11:22... / COM3");
        box.addView(label("MAC Bluetooth / Porta COM"));
        box.addView(mac);

        Spinner spPapel = new Spinner(this);
        ArrayAdapter<String> papelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[] {"80", "58"});
        spPapel.setAdapter(papelAdapter);
        box.addView(label("Tamanho do papel"));
        box.addView(spPapel);

        Spinner spDriver = new Spinner(this);
        ArrayAdapter<String> driverAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ThermalPrinterDriver.getDisplayNames());
        spDriver.setAdapter(driverAdapter);
        box.addView(label("Driver / modelo da impressora"));
        box.addView(spDriver);

        final String[] impressoraSistemaSelecionada = new String[] { editar != null ? nvl(editar.impressoraSistema, "") : "" };

        spImpressora.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                PrinterOption opt = position >= 0 && position < opcoesImpressora.size() ? opcoesImpressora.get(position) : null;
                if (opt == null || opt.manual) return;
                aplicarOpcaoImpressora(opt, nomeImp, spTipo, ip, porta, mac);
                impressoraSistemaSelecionada[0] = opt.printServerPrinter;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnBuscarInstaladas.setOnClickListener(v -> buscarImpressorasInstaladasAsync(opcoesImpressora, impAdapter, spImpressora));

        if (editar != null) {
            for (int i = 0; i < categorias.size(); i++) if (categorias.get(i).id == editar.categoriaId) spCategoria.setSelection(i);
            nomeImp.setText(editar.nomeImpressora);
            for (int i = 0; i < tipos.length; i++) if (tipos[i].equals(editar.tipo)) spTipo.setSelection(i);
            ip.setText(editar.ip);
            porta.setText(String.valueOf(editar.porta));
            mac.setText(editar.mac);
            spPapel.setSelection(editar.papel == 58 ? 1 : 0);
            spDriver.setSelection(ThermalPrinterDriver.findPosition(editar.driverId));
        } else {
            porta.setText("9100");
            spDriver.setSelection(0);
        }

        selecionarOpcaoAtual(spImpressora, opcoesImpressora, editar);

        spCategoria.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (nomeImp.getText().toString().trim().isEmpty()) nomeImp.setText(categorias.get(position).nome);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        new AlertDialog.Builder(this)
                .setTitle(editar == null ? "Nova multiimpressora" : "Editar multiimpressora")
                .setView(enveloparDialogScroll(box))
                .setPositiveButton("Salvar", (d, w) -> {
                    Categoria c = (Categoria) spCategoria.getSelectedItem();
                    MultiPrinterManager.Rule r = new MultiPrinterManager.Rule();
                    r.ativo = ativo.isChecked();
                    r.categoriaId = c.id;
                    r.categoriaNome = c.nome;
                    r.nomeImpressora = nomeImp.getText().toString().trim();
                    r.tipo = (String) spTipo.getSelectedItem();
                    r.ip = ip.getText().toString().trim();
                    try { r.porta = Integer.parseInt(porta.getText().toString().trim()); } catch (Exception e) { r.porta = 9100; }
                    r.mac = mac.getText().toString().trim();
                    r.impressoraSistema = impressoraSistemaSelecionada[0] == null ? "" : impressoraSistemaSelecionada[0].trim();
                    try { r.papel = Integer.parseInt((String) spPapel.getSelectedItem()); } catch (Exception e) { r.papel = 80; }
                    String[] driverIds = ThermalPrinterDriver.getIds();
                    int posDriver = spDriver.getSelectedItemPosition();
                    if (!PermissionHelper.verificar(this, PermissionConstants.MULTIIMPRESSORAS_CONFIGURAR_DRIVER)) return;
                    r.driverId = (posDriver >= 0 && posDriver < driverIds.length) ? driverIds[posDriver] : ThermalPrinterDriver.DRIVER_AUTO;
                    manager.addOrReplaceRule(r);
                    recarregarLista();
                    showToast("Regra salva.");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }



    private ArrayList<PrinterOption> carregarOpcoesImpressoras(MultiPrinterManager.Rule editar) {
        ArrayList<PrinterOption> list = new ArrayList<>();
        list.add(PrinterOption.manual());

        try {
            PrinterManager pm = new PrinterManager(this);
            String tipo = pm.getTipoImpressora();
            if (tipo != null && !tipo.trim().isEmpty() && !PrinterManager.TIPO_NENHUMA.equals(tipo)) {
                PrinterOption comum = new PrinterOption();
                comum.label = "Usar impressora comum configurada do sistema";
                comum.tipo = tipo;
                comum.ip = nvl(pm.getIpImpressora(), "");
                comum.porta = pm.getPortaImpressora();
                comum.mac = nvl(pm.getMacBluetooth(), "");
                comum.nome = "Impressora comum";
                comum.driverId = pm.getDriverId();
                comum.papel = pm.getTamanhoPapel();
                if (PrinterManager.TIPO_PRINT_SERVER.equals(tipo)) {
                    comum.ip = nvl(pm.getPrintServerIp(), comum.ip);
                    comum.porta = pm.getPrintServerPorta();
                    comum.printServerPrinter = nvl(pm.getPrintServerImpressora(), "");
                    comum.nome = comum.printServerPrinter.isEmpty() ? comum.nome : comum.printServerPrinter;
                }
                list.add(comum);
            }
        } catch (Exception ignored) {}

        list.addAll(listarBluetoothPareados());

        if (editar != null && !nvl(editar.nomeImpressora, "").isEmpty()) {
            PrinterOption atual = new PrinterOption();
            atual.label = "Configuracao atual: " + editar.nomeImpressora;
            atual.nome = editar.nomeImpressora;
            atual.tipo = editar.tipo;
            atual.ip = editar.ip;
            atual.porta = editar.porta;
            atual.mac = editar.mac;
            atual.papel = editar.papel;
            atual.driverId = editar.driverId;
            atual.printServerPrinter = editar.impressoraSistema;
            list.add(atual);
        }
        return list;
    }

    private ArrayList<PrinterOption> listarBluetoothPareados() {
        ArrayList<PrinterOption> list = new ArrayList<>();
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return list;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                list.add(PrinterOption.info("Bluetooth pareado indisponivel: permita BLUETOOTH_CONNECT nas permissoes do app."));
                return list;
            }
            Set<BluetoothDevice> devices = adapter.getBondedDevices();
            if (devices == null) return list;
            for (BluetoothDevice d : devices) {
                String name = "Dispositivo Bluetooth";
                String mac = "";
                try { name = d.getName() == null ? name : d.getName(); } catch (SecurityException ignored) {}
                try { mac = d.getAddress() == null ? "" : d.getAddress(); } catch (SecurityException ignored) {}
                if (!mac.isEmpty()) {
                    PrinterOption opt = new PrinterOption();
                    opt.label = "Bluetooth pareada: " + name + " (" + mac + ")";
                    opt.nome = name;
                    opt.tipo = PrinterManager.TIPO_BLUETOOTH;
                    opt.mac = mac;
                    opt.ip = "";
                    opt.porta = 9100;
                    opt.papel = 80;
                    opt.driverId = ThermalPrinterDriver.DRIVER_AUTO;
                    list.add(opt);
                }
            }
        } catch (Exception e) {
            Log.e("MultiImpressoras", "Erro ao listar Bluetooth pareado", e);
        }
        return list;
    }

    private void buscarImpressorasInstaladasAsync(ArrayList<PrinterOption> opcoes, ArrayAdapter<PrinterOption> adapter, Spinner spinner) {
        PrinterManager pm = new PrinterManager(this);
        String ip = pm.getPrintServerIp();
        int porta = pm.getPrintServerPorta();
        if (ip == null || ip.trim().isEmpty()) {
            showError("Configure primeiro o Print Server em Configurar Impressora.\n\nAssim o sistema consegue listar as impressoras instaladas no computador e permitir a escolha aqui na Multiimpressoras.");
            return;
        }
        showToast("Buscando impressoras instaladas em " + ip + ":" + porta + "...");
        new Thread(() -> {
            List<String> nomes = pm.listarImpressorasPrintServer();
            runOnUiThread(() -> {
                if (nomes == null || nomes.isEmpty()) {
                    showError("Nenhuma impressora instalada foi retornada pelo Print Server.\n\nVerifique se o PDV_Print_Server.py esta aberto no computador e se ha impressoras instaladas no Windows.");
                    return;
                }
                for (String raw : nomes) {
                    String nome = raw == null ? "" : raw.replace(" [PADRAO]", "").trim();
                    if (nome.isEmpty()) continue;
                    PrinterOption opt = new PrinterOption();
                    opt.label = "Instalada no PC/Print Server: " + raw;
                    opt.nome = nome;
                    opt.tipo = PrinterManager.TIPO_PRINT_SERVER;
                    opt.ip = ip.trim();
                    opt.porta = porta <= 0 ? 9200 : porta;
                    opt.printServerPrinter = nome;
                    opt.papel = 80;
                    opt.driverId = ThermalPrinterDriver.DRIVER_AUTO;
                    opcoes.add(opt);
                }
                adapter.notifyDataSetChanged();
                spinner.setSelection(Math.max(0, opcoes.size() - nomes.size()));
                showToast("Impressoras instaladas carregadas. Escolha uma na lista.");
            });
        }).start();
    }

    private void aplicarOpcaoImpressora(PrinterOption opt, EditText nomeImp, Spinner spTipo, EditText ip, EditText porta, EditText mac) {
        if (opt == null || opt.info) return;
        if (!nvl(opt.nome, "").isEmpty()) nomeImp.setText(opt.nome);
        for (int i = 0; i < tipos.length; i++) {
            if (tipos[i].equals(opt.tipo)) {
                spTipo.setSelection(i);
                break;
            }
        }
        ip.setText(nvl(opt.ip, ""));
        porta.setText(String.valueOf(opt.porta <= 0 ? (PrinterManager.TIPO_PRINT_SERVER.equals(opt.tipo) ? 9200 : 9100) : opt.porta));
        mac.setText(nvl(opt.mac, ""));
    }

    private void selecionarOpcaoAtual(Spinner spinner, ArrayList<PrinterOption> opcoes, MultiPrinterManager.Rule editar) {
        if (editar == null || opcoes == null) return;
        for (int i = 0; i < opcoes.size(); i++) {
            PrinterOption opt = opcoes.get(i);
            if (opt == null || opt.manual || opt.info) continue;
            boolean mesmoBt = PrinterManager.TIPO_BLUETOOTH.equals(editar.tipo) && !nvl(editar.mac, "").isEmpty() && nvl(editar.mac, "").equalsIgnoreCase(nvl(opt.mac, ""));
            boolean mesmoPs = PrinterManager.TIPO_PRINT_SERVER.equals(editar.tipo) && !nvl(editar.impressoraSistema, "").isEmpty() && nvl(editar.impressoraSistema, "").equalsIgnoreCase(nvl(opt.printServerPrinter, ""));
            boolean mesmoNome = !nvl(editar.nomeImpressora, "").isEmpty() && nvl(editar.nomeImpressora, "").equalsIgnoreCase(nvl(opt.nome, ""));
            if (mesmoBt || mesmoPs || mesmoNome) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private List<Categoria> carregarCategorias() {
        return carregarCategoriasSeguro();
    }

    private List<Categoria> carregarCategoriasSeguro() {
        List<Categoria> list = new ArrayList<>();
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(this);
            Connection conn = db.getConnection();
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT id, descricao FROM tipos_produto WHERE ativo = 1 ORDER BY descricao");
            while (rs.next()) list.add(new Categoria(rs.getInt("id"), rs.getString("descricao")));
            rs.close();
            st.close();
        } catch (Exception e) {
            Log.e("MultiImpressoras", "Erro ao carregar categorias", e);
        }
        return list;
    }

    private ScrollView enveloparDialogScroll(View conteudo) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(conteudo);
        int maxAltura = (int) (getResources().getDisplayMetrics().heightPixels * 0.78f);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxAltura));
        return scroll;
    }

    private TextView titulo(String s, int sp) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(Color.rgb(0, 230, 255));
        v.setTextSize(sp);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setPadding(0, dp(6), 0, dp(6));
        return v;
    }

    private TextView texto(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(Color.rgb(220, 232, 240));
        v.setTextSize(14);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    private TextView label(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(Color.rgb(0, 230, 255));
        v.setTextSize(13);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setPadding(0, dp(10), 0, dp(2));
        return v;
    }

    private EditText campo(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        return e;
    }

    private Button botao(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.BLACK);
        b.setTextSize(12);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        return b;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private String nvl(String s, String alt) { return s == null || s.trim().isEmpty() ? alt : s.trim(); }

    private static class PrinterOption {
        boolean manual;
        boolean info;
        String label = "";
        String nome = "";
        String tipo = PrinterManager.TIPO_REDE;
        String ip = "";
        int porta = 9100;
        String mac = "";
        int papel = 80;
        String driverId = ThermalPrinterDriver.DRIVER_AUTO;
        String printServerPrinter = "";

        static PrinterOption manual() {
            PrinterOption o = new PrinterOption();
            o.manual = true;
            o.label = "Digitar/configurar manualmente nos campos abaixo";
            return o;
        }

        static PrinterOption info(String msg) {
            PrinterOption o = new PrinterOption();
            o.info = true;
            o.label = msg;
            return o;
        }

        @Override public String toString() { return label; }
    }

    private static class Categoria {
        int id; String nome;
        Categoria(int id, String nome) { this.id = id; this.nome = nome == null ? "" : nome; }
        @Override public String toString() { return nome; }
    }
}
