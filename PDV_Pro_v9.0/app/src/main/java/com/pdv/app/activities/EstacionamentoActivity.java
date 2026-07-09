
package com.pdv.app.activities;

import androidx.appcompat.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.speech.RecognizerIntent;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.PrinterManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Gerenciamento profissional de estacionamento.
 * Recursos:
 * - Entrada de veiculos com ticket automatico
 * - Controle de vaga, placa, condutor, telefone e observacao
 * - Listagem de veiculos em aberto e historico rapido do dia
 * - Finalizacao com calculo automatico por hora/fracao
 * - Cancelamento/baixa manual com confirmacao
 * - Auto-criacao e auto-reparo da tabela no MySQL
 */
public class EstacionamentoActivity extends BaseActivity {
    private static final int REQ_DITAR_PLACA_ESTACIONAMENTO = 8711;
    private static final String STATUS_ABERTO = "ABERTO";
    private static final String STATUS_FECHADO = "FECHADO";
    private static final String STATUS_CANCELADO = "CANCELADO";

    private final DecimalFormat money = new DecimalFormat("0.00");
    private DatabaseHelper db;
    private LinearLayout listaContainer;
    private TextView tvResumo;
    private EditText edtBusca;
    private int veiculoSelecionadoId = -1;
    private String veiculoSelecionadoPlaca = "";
    private EditText campoPlacaLeitorAtivo;

    private int cyan = Color.parseColor("#00BCD4");
    private int bg = Color.parseColor("#0A0E27");
    private int card = Color.parseColor("#141832");
    private int text = Color.WHITE;
    private int hint = Color.parseColor("#B0BEC5");
    private int success = Color.parseColor("#00E676");
    private int danger = Color.parseColor("#FF5252");
    private int warning = Color.parseColor("#FFD740");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.ESTACIONAMENTO_ACESSAR)) { return; }
        db = DatabaseHelper.getInstance(this);
        montarTela();
        inicializarBancoECarregar();
    }

    private void montarTela() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(20));
        scroll.addView(root);

        LinearLayout topo = new LinearLayout(this);
        topo.setOrientation(LinearLayout.HORIZONTAL);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(topo, new LinearLayout.LayoutParams(-1, -2));

        Button voltar = botao("←", cyan, Color.BLACK);
        topo.addView(voltar, new LinearLayout.LayoutParams(dp(54), dp(48)));
        voltar.setOnClickListener(v -> finish());

        TextView titulo = texto("Gerenciamento de Estacionamento", 22, true, cyan);
        titulo.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tituloLp = new LinearLayout.LayoutParams(0, dp(54), 1);
        tituloLp.leftMargin = dp(10);
        topo.addView(titulo, tituloLp);

        tvResumo = texto("Carregando...", 13, false, hint);
        tvResumo.setPadding(0, dp(8), 0, dp(8));
        root.addView(tvResumo, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout acoes = new LinearLayout(this);
        acoes.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(acoes, new LinearLayout.LayoutParams(-1, -2));

        Button btnEntrada = botao("NOVA\nENTRADA", success, Color.BLACK);
        Button btnSaida = botao("FINALIZAR\nSAÍDA", cyan, Color.BLACK);
        Button btnCancelar = botao("CANCELAR", danger, Color.WHITE);
        acoes.addView(btnEntrada, pesoLp());
        acoes.addView(btnSaida, pesoLp());
        acoes.addView(btnCancelar, pesoLp());

        btnEntrada.setOnClickListener(v -> { if (PermissionHelper.verificar(this, PermissionConstants.ESTACIONAMENTO_ENTRADA)) abrirDialogEntrada(); });
        btnSaida.setOnClickListener(v -> { if (PermissionHelper.verificar(this, PermissionConstants.ESTACIONAMENTO_FINALIZAR_SAIDA)) finalizarSelecionado(); });
        btnCancelar.setOnClickListener(v -> { if (PermissionHelper.verificar(this, PermissionConstants.ESTACIONAMENTO_CANCELAR)) cancelarSelecionado(); });

        LinearLayout buscaBar = new LinearLayout(this);
        buscaBar.setOrientation(LinearLayout.HORIZONTAL);
        buscaBar.setGravity(Gravity.CENTER_VERTICAL);
        buscaBar.setPadding(0, dp(10), 0, dp(6));
        root.addView(buscaBar, new LinearLayout.LayoutParams(-1, -2));

        edtBusca = campo("Buscar por placa, vaga ou condutor");
        buscaBar.addView(edtBusca, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button btnBuscar = botao("BUSCAR", cyan, Color.BLACK);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(dp(100), dp(50));
        bLp.leftMargin = dp(8);
        buscaBar.addView(btnBuscar, bLp);
        btnBuscar.setOnClickListener(v -> carregarLista());

        LinearLayout filtros = new LinearLayout(this);
        filtros.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(filtros, new LinearLayout.LayoutParams(-1, -2));
        Button btnAbertos = botao("ABERTOS", warning, Color.BLACK);
        Button btnHoje = botao("HOJE", cyan, Color.BLACK);
        Button btnAtualizar = botao("ATUALIZAR", cyan, Color.BLACK);
        filtros.addView(btnAbertos, pesoLp());
        filtros.addView(btnHoje, pesoLp());
        filtros.addView(btnAtualizar, pesoLp());
        btnAbertos.setOnClickListener(v -> { edtBusca.setText(""); carregarLista(); });
        btnHoje.setOnClickListener(v -> carregarHistoricoHoje());
        btnAtualizar.setOnClickListener(v -> carregarLista());

        TextView subtitulo = texto("Veículos no estacionamento", 16, true, text);
        subtitulo.setPadding(0, dp(12), 0, dp(6));
        root.addView(subtitulo);

        listaContainer = new LinearLayout(this);
        listaContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listaContainer, new LinearLayout.LayoutParams(-1, -2));

        setContentView(scroll);
    }

    /**
     * Inicializa o modulo e carrega a lista.
     * A tabela 'estacionamento' e criada/verificada pelo DatabaseHelper na inicializacao do sistema.
     */
    private void inicializarBancoECarregar() {
        carregarLista();
    }

    private void abrirDialogEntrada() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        box.setPadding(pad, pad, pad, pad);

        EditText placa = campoDialog("Placa obrigatória");
        placa.setSingleLine(true);
        placa.setAllCaps(true);

        Button btnLeitorPlaca = botao("📷 LEITOR INTELIGENTE DE PLACA", cyan, Color.BLACK);
        btnLeitorPlaca.setTextSize(13);
        btnLeitorPlaca.setOnClickListener(v -> { if (PermissionHelper.verificar(this, PermissionConstants.ESTACIONAMENTO_LEITOR_PLACA)) abrirLeitorInteligentePlaca(placa); });

        TextView dicaPlaca = texto("Você pode ler/falar a placa automaticamente ou digitar manualmente.", 12, false, Color.DKGRAY);

        TextView lblCarro = texto("Nome do carro / modelo", 13, true, Color.DKGRAY);
        Spinner spNomeCarro = new Spinner(this);
        String[] nomesCarros = new String[]{
                "Selecione o nome do carro",
                "Chevrolet Agile",
                "Chevrolet Astra",
                "Chevrolet Blazer",
                "Chevrolet Bolt",
                "Chevrolet Camaro",
                "Chevrolet Captiva",
                "Chevrolet Celta",
                "Chevrolet Classic",
                "Chevrolet Cobalt",
                "Chevrolet Corsa",
                "Chevrolet Cruze",
                "Chevrolet D20",
                "Chevrolet Equinox",
                "Chevrolet Kadett",
                "Chevrolet Meriva",
                "Chevrolet Montana",
                "Chevrolet Monza",
                "Chevrolet Omega",
                "Chevrolet Onix",
                "Chevrolet Prisma",
                "Chevrolet S10",
                "Chevrolet Sonic",
                "Chevrolet Spin",
                "Chevrolet Tracker",
                "Chevrolet Trailblazer",
                "Chevrolet Vectra",
                "Chevrolet Zafira",
                "Fiat 500",
                "Fiat Argo",
                "Fiat Bravo",
                "Fiat Cronos",
                "Fiat Doblo",
                "Fiat Ducato",
                "Fiat Fiorino",
                "Fiat Freemont",
                "Fiat Grand Siena",
                "Fiat Idea",
                "Fiat Linea",
                "Fiat Marea",
                "Fiat Mobi",
                "Fiat Palio",
                "Fiat Punto",
                "Fiat Siena",
                "Fiat Stilo",
                "Fiat Strada",
                "Fiat Tempra",
                "Fiat Tipo",
                "Fiat Toro",
                "Fiat Uno",
                "Fiat Weekend",
                "Volkswagen Amarok",
                "Volkswagen Bora",
                "Volkswagen Brasilia",
                "Volkswagen CrossFox",
                "Volkswagen Fox",
                "Volkswagen Fusca",
                "Volkswagen Gol",
                "Volkswagen Golf",
                "Volkswagen Jetta",
                "Volkswagen Kombi",
                "Volkswagen Logus",
                "Volkswagen Nivus",
                "Volkswagen Parati",
                "Volkswagen Passat",
                "Volkswagen Polo",
                "Volkswagen Quantum",
                "Volkswagen Santana",
                "Volkswagen Saveiro",
                "Volkswagen SpaceFox",
                "Volkswagen T-Cross",
                "Volkswagen Taos",
                "Volkswagen Tiguan",
                "Volkswagen Up",
                "Volkswagen Virtus",
                "Volkswagen Voyage",
                "Ford Belina",
                "Ford Bronco",
                "Ford Cargo",
                "Ford Corcel",
                "Ford Courier",
                "Ford EcoSport",
                "Ford Edge",
                "Ford Escort",
                "Ford F-1000",
                "Ford F-250",
                "Ford Fiesta",
                "Ford Focus",
                "Ford Fusion",
                "Ford Ka",
                "Ford Maverick",
                "Ford Mondeo",
                "Ford Mustang",
                "Ford Pampa",
                "Ford Ranger",
                "Ford Territory",
                "Ford Transit",
                "Ford Verona",
                "Toyota Bandeirante",
                "Toyota Camry",
                "Toyota Corolla",
                "Toyota Corolla Cross",
                "Toyota Etios",
                "Toyota Fielder",
                "Toyota Hilux",
                "Toyota Land Cruiser",
                "Toyota Prius",
                "Toyota RAV4",
                "Toyota SW4",
                "Toyota Yaris",
                "Honda Accord",
                "Honda City",
                "Honda Civic",
                "Honda CR-V",
                "Honda Fit",
                "Honda HR-V",
                "Honda WR-V",
                "Honda ZR-V",
                "Hyundai Azera",
                "Hyundai Creta",
                "Hyundai Elantra",
                "Hyundai HB20",
                "Hyundai HB20S",
                "Hyundai HR",
                "Hyundai i30",
                "Hyundai ix35",
                "Hyundai Santa Fe",
                "Hyundai Sonata",
                "Hyundai Tucson",
                "Hyundai Veloster",
                "Hyundai Veracruz",
                "Jeep Cherokee",
                "Jeep Commander",
                "Jeep Compass",
                "Jeep Grand Cherokee",
                "Jeep Renegade",
                "Jeep Wrangler",
                "Renault Captur",
                "Renault Clio",
                "Renault Duster",
                "Renault Fluence",
                "Renault Kangoo",
                "Renault Koleos",
                "Renault Kwid",
                "Renault Logan",
                "Renault Master",
                "Renault Megane",
                "Renault Oroch",
                "Renault Sandero",
                "Renault Scenic",
                "Renault Symbol",
                "Renault Trafic",
                "Nissan Frontier",
                "Nissan Kicks",
                "Nissan Livina",
                "Nissan March",
                "Nissan Pathfinder",
                "Nissan Sentra",
                "Nissan Tiida",
                "Nissan Versa",
                "Nissan X-Trail",
                "Peugeot 106",
                "Peugeot 2008",
                "Peugeot 205",
                "Peugeot 206",
                "Peugeot 207",
                "Peugeot 208",
                "Peugeot 3008",
                "Peugeot 306",
                "Peugeot 307",
                "Peugeot 308",
                "Peugeot 405",
                "Peugeot 406",
                "Peugeot 408",
                "Peugeot 5008",
                "Peugeot Boxer",
                "Peugeot Expert",
                "Peugeot Hoggar",
                "Peugeot Partner",
                "Peugeot RCZ",
                "Citroen Aircross",
                "Citroen Berlingo",
                "Citroen C3",
                "Citroen C3 Aircross",
                "Citroen C4",
                "Citroen C4 Cactus",
                "Citroen C4 Lounge",
                "Citroen C5",
                "Citroen DS3",
                "Citroen DS4",
                "Citroen DS5",
                "Citroen Jumpy",
                "Citroen Xantia",
                "Citroen Xsara Picasso",
                "Mitsubishi ASX",
                "Mitsubishi Eclipse Cross",
                "Mitsubishi L200",
                "Mitsubishi Lancer",
                "Mitsubishi Outlander",
                "Mitsubishi Pajero",
                "Mitsubishi Pajero Dakar",
                "Mitsubishi Pajero Full",
                "Mitsubishi Pajero Sport",
                "Mitsubishi Space Wagon",
                "Mitsubishi Triton",
                "Kia Besta",
                "Kia Bongo",
                "Kia Carnival",
                "Kia Cerato",
                "Kia Magentis",
                "Kia Mohave",
                "Kia Picanto",
                "Kia Rio",
                "Kia Sorento",
                "Kia Soul",
                "Kia Sportage",
                "Mercedes-Benz A200",
                "Mercedes-Benz B200",
                "Mercedes-Benz C180",
                "Mercedes-Benz C200",
                "Mercedes-Benz C250",
                "Mercedes-Benz C300",
                "Mercedes-Benz CLA",
                "Mercedes-Benz Classe A",
                "Mercedes-Benz Classe B",
                "Mercedes-Benz Classe C",
                "Mercedes-Benz Classe E",
                "Mercedes-Benz Classe G",
                "Mercedes-Benz Classe S",
                "Mercedes-Benz GLA",
                "Mercedes-Benz GLB",
                "Mercedes-Benz GLC",
                "Mercedes-Benz GLE",
                "Mercedes-Benz GLS",
                "Mercedes-Benz Sprinter",
                "Mercedes-Benz Vito",
                "BMW 116i",
                "BMW 118i",
                "BMW 120i",
                "BMW 130i",
                "BMW 218i",
                "BMW 320i",
                "BMW 325i",
                "BMW 328i",
                "BMW 330i",
                "BMW 420i",
                "BMW 430i",
                "BMW 528i",
                "BMW 530i",
                "BMW M3",
                "BMW M4",
                "BMW X1",
                "BMW X2",
                "BMW X3",
                "BMW X4",
                "BMW X5",
                "BMW X6",
                "BMW X7",
                "BMW Z4",
                "Audi A1",
                "Audi A3",
                "Audi A4",
                "Audi A5",
                "Audi A6",
                "Audi A7",
                "Audi A8",
                "Audi Q2",
                "Audi Q3",
                "Audi Q5",
                "Audi Q7",
                "Audi Q8",
                "Audi RS3",
                "Audi RS4",
                "Audi RS5",
                "Audi TT",
                "Volvo C30",
                "Volvo S40",
                "Volvo S60",
                "Volvo S90",
                "Volvo V40",
                "Volvo V60",
                "Volvo XC40",
                "Volvo XC60",
                "Volvo XC90",
                "Land Rover Defender",
                "Land Rover Discovery",
                "Land Rover Discovery Sport",
                "Land Rover Freelander",
                "Land Rover Range Rover",
                "Land Rover Range Rover Evoque",
                "Land Rover Range Rover Sport",
                "Land Rover Range Rover Velar",
                "Jaguar E-Pace",
                "Jaguar F-Pace",
                "Jaguar XE",
                "Jaguar XF",
                "Porsche 911",
                "Porsche Boxster",
                "Porsche Cayenne",
                "Porsche Cayman",
                "Porsche Macan",
                "Porsche Panamera",
                "Caoa Chery Arrizo 5",
                "Caoa Chery Arrizo 6",
                "Caoa Chery Celer",
                "Caoa Chery Face",
                "Caoa Chery iCar",
                "Caoa Chery QQ",
                "Caoa Chery Tiggo 2",
                "Caoa Chery Tiggo 3X",
                "Caoa Chery Tiggo 5X",
                "Caoa Chery Tiggo 7",
                "Caoa Chery Tiggo 8",
                "BYD Dolphin",
                "BYD Dolphin Mini",
                "BYD Han",
                "BYD King",
                "BYD Seal",
                "BYD Song Plus",
                "BYD Tan",
                "GWM Haval H6",
                "GWM Ora 03",
                "JAC J2",
                "JAC J3",
                "JAC J5",
                "JAC J6",
                "JAC T40",
                "JAC T50",
                "JAC T60",
                "JAC T80",
                "JAC iEV20",
                "JAC iEV40",
                "Subaru Forester",
                "Subaru Impreza",
                "Subaru Legacy",
                "Subaru Outback",
                "Subaru Tribeca",
                "Suzuki Grand Vitara",
                "Suzuki Jimny",
                "Suzuki S-Cross",
                "Suzuki Swift",
                "Suzuki Vitara",
                "Mazda 3",
                "Mazda 6",
                "Mazda CX-3",
                "Mazda CX-5",
                "Mazda MX-5",
                "Lexus ES",
                "Lexus NX",
                "Lexus RX",
                "Mini Cooper",
                "Mini Countryman",
                "Mini John Cooper Works",
                "Ram 1500",
                "Ram 2500",
                "Ram 3500",
                "Dodge Dakota",
                "Dodge Journey",
                "Dodge Ram",
                "Chrysler 300C",
                "Chrysler Caravan",
                "Chrysler PT Cruiser",
                "Chrysler Town & Country",
                "Troller T4",
                "Mahindra Scorpio",
                "Hummer H2",
                "Hummer H3",
                "Agrale Marrua",
                "Iveco Daily",
                "Iveco Tector",
                "Iveco Vertis",
                "Foton Aumark",
                "Foton Tunland",
                "Volkswagen Delivery",
                "Volkswagen Worker",
                "Volkswagen Constellation",
                "Mercedes-Benz Accelo",
                "Mercedes-Benz Atego",
                "Mercedes-Benz Axor",
                "Mercedes-Benz Actros",
                "Scania P Series",
                "Scania G Series",
                "Scania R Series",
                "Volvo FH",
                "Volvo FM",
                "Volvo VM",
                "Ford F-4000",
                "Citroen Jumper",
                "Jinbei Topic",
                "Van Escolar",
                "Micro-ônibus",
                "Ônibus",
                "Honda Biz",
                "Honda Bros",
                "Honda CG 125",
                "Honda CG 150",
                "Honda CG 160",
                "Honda CB 250 Twister",
                "Honda CB 300",
                "Honda CB 500",
                "Honda XRE 190",
                "Honda XRE 300",
                "Honda PCX",
                "Honda Pop 100",
                "Honda Pop 110i",
                "Yamaha Crypton",
                "Yamaha Factor",
                "Yamaha Fazer 150",
                "Yamaha Fazer 250",
                "Yamaha Fazer 600",
                "Yamaha Lander",
                "Yamaha MT-03",
                "Yamaha MT-07",
                "Yamaha NMax",
                "Yamaha Neo",
                "Yamaha R3",
                "Yamaha XJ6",
                "Yamaha XTZ 125",
                "Yamaha YBR",
                "Suzuki Burgman",
                "Suzuki Yes",
                "Suzuki Intruder",
                "Dafra Apache",
                "Dafra Citycom",
                "Dafra Kansas",
                "Dafra Next",
                "Haojue DK 150",
                "Haojue DR 160",
                "Shineray Jet",
                "Shineray Phoenix",
                "Kawasaki Ninja",
                "Kawasaki Versys",
                "Kawasaki Z300",
                "Kawasaki Z650",
                "BMW G 310",
                "Harley-Davidson Sportster",
                "Harley-Davidson Fat Boy",
                "Ducati Monster",
                "Triumph Tiger",
                "Carro",
                "Moto",
                "Van",
                "Caminhonete",
                "Caminhão",
                "Triciclo",
                "Quadriciclo",
                "Reboque",
                "Carretinha",
                "Outro / digitar manualmente"
        };
        ArrayAdapter<String> carroAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, nomesCarros);
        carroAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spNomeCarro.setAdapter(carroAdapter);

        EditText veiculo = campoDialog("Digite o modelo se não estiver na lista");
        EditText condutor = campoDialog("Condutor/cliente opcional");
        EditText telefone = campoDialog("Telefone opcional");
        EditText vaga = campoDialog("Vaga opcional");
        EditText tipo = campoDialog("Tipo: CARRO, MOTO, VAN...");
        EditText valorHora = campoDialog("Valor por hora");
        EditText obs = campoDialog("Observação opcional");
        valorHora.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        valorHora.setText("5.00");
        tipo.setText("CARRO");

        box.addView(placa);
        LinearLayout.LayoutParams leitorLp = new LinearLayout.LayoutParams(-1, dp(48));
        leitorLp.setMargins(0, dp(6), 0, dp(3));
        box.addView(btnLeitorPlaca, leitorLp);
        box.addView(dicaPlaca);
        box.addView(lblCarro);
        box.addView(spNomeCarro, new LinearLayout.LayoutParams(-1, dp(48)));
        box.addView(veiculo);
        box.addView(condutor); box.addView(telefone);
        box.addView(vaga); box.addView(tipo); box.addView(valorHora); box.addView(obs);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Nova entrada no estacionamento")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Registrar entrada", null)
                .create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String p = normalizarPlacaInteligente(placa.getText().toString());
            placa.setText(p);
            if (p.length() < 3) {
                placa.setError("Informe a placa");
                return;
            }
            double vh = parseDouble(valorHora.getText().toString(), 0);
            if (vh < 0) vh = 0;
            String carroSelecionado = spNomeCarro.getSelectedItem() == null ? "" : spNomeCarro.getSelectedItem().toString().trim();
            String carroDigitado = veiculo.getText().toString().trim();
            String nomeCarroFinal;
            if (carroDigitado.length() > 0) {
                nomeCarroFinal = carroDigitado;
            } else if (spNomeCarro.getSelectedItemPosition() > 0 && !carroSelecionado.toUpperCase(Locale.ROOT).contains("OUTRO")) {
                nomeCarroFinal = carroSelecionado;
            } else {
                nomeCarroFinal = "";
            }
            registrarEntrada(dlg, p, nomeCarroFinal, condutor.getText().toString(), telefone.getText().toString(), vaga.getText().toString(), tipo.getText().toString(), vh, obs.getText().toString());
        }));
        dlg.show();
    }

    private void registrarEntrada(AlertDialog dlg, String placa, String veiculo, String condutor, String telefone, String vaga, String tipo, double valorHora, String obs) {
        showLoading("Registrando entrada...");
        new Thread(() -> {
            try {
                String ticket = gerarTicket();
                db.executeWithRetryVoid(conn -> {
                    PreparedStatement ps = conn.prepareStatement("INSERT INTO estacionamento (ticket, placa, veiculo, condutor, telefone, vaga, tipo, entrada, status, valor_hora, valor_total, observacao) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), 'ABERTO', ?, 0, ?)");
                    try {
                        ps.setString(1, ticket);
                        ps.setString(2, placa);
                        ps.setString(3, vazioNull(veiculo));
                        ps.setString(4, vazioNull(condutor));
                        ps.setString(5, vazioNull(telefone));
                        ps.setString(6, vazioNull(vaga));
                        ps.setString(7, vazioNull(tipo));
                        ps.setDouble(8, valorHora);
                        ps.setString(9, vazioNull(obs));
                        ps.executeUpdate();
                    } finally { ps.close(); }
                });
                runOnUiThread(() -> {
                    hideLoading();
                    dlg.dismiss();
                    showSuccess("Entrada registrada com sucesso.\n\nTicket: " + ticket + "\nPlaca: " + placa);
                    imprimirComprovanteChegada(ticket, placa, veiculo, condutor, telefone, vaga, tipo, valorHora, obs);
                    carregarLista();
                });
            } catch (Exception e) {
                hideLoading();
                showError("Erro ao registrar entrada: " + e.getMessage());
            }
        }).start();
    }

    private void carregarLista() {
        String busca = edtBusca == null ? "" : edtBusca.getText().toString().trim();
        showLoading("Carregando estacionamento...");
        new Thread(() -> {
            try {
                ArrayList<Registro> dados = buscarRegistrosAbertos(busca);
                Resumo resumo = buscarResumo();
                runOnUiThread(() -> {
                    hideLoading();
                    renderizarLista(dados, resumo, "Veículos em aberto");
                });
            } catch (Exception e) {
                hideLoading();
                showError("Erro ao carregar estacionamento: " + e.getMessage());
            }
        }).start();
    }

    private void carregarHistoricoHoje() {
        showLoading("Carregando histórico de hoje...");
        new Thread(() -> {
            try {
                ArrayList<Registro> dados = buscarHistoricoHojeDb();
                Resumo resumo = buscarResumo();
                runOnUiThread(() -> {
                    hideLoading();
                    renderizarLista(dados, resumo, "Histórico de hoje");
                });
            } catch (Exception e) {
                hideLoading();
                showError("Erro ao carregar histórico: " + e.getMessage());
            }
        }).start();
    }

    private ArrayList<Registro> buscarRegistrosAbertos(String busca) throws SQLException {
        return db.executeWithRetry(conn -> {
            ArrayList<Registro> out = new ArrayList<>();
            boolean filtra = busca != null && busca.trim().length() > 0;
            String sql = "SELECT *, TIMESTAMPDIFF(MINUTE, entrada, NOW()) AS minutos FROM estacionamento WHERE status='ABERTO'" +
                    (filtra ? " AND (placa LIKE ? OR vaga LIKE ? OR condutor LIKE ? OR ticket LIKE ?)" : "") +
                    " ORDER BY entrada DESC LIMIT 200";
            PreparedStatement ps = conn.prepareStatement(sql);
            try {
                if (filtra) {
                    String b = "%" + busca + "%";
                    ps.setString(1, b); ps.setString(2, b); ps.setString(3, b); ps.setString(4, b);
                }
                ResultSet rs = ps.executeQuery();
                try { while (rs.next()) out.add(fromRs(rs)); } finally { rs.close(); }
            } finally { ps.close(); }
            return out;
        });
    }

    private ArrayList<Registro> buscarHistoricoHojeDb() throws SQLException {
        return db.executeWithRetry(conn -> {
            ArrayList<Registro> out = new ArrayList<>();
            PreparedStatement ps = conn.prepareStatement("SELECT *, TIMESTAMPDIFF(MINUTE, entrada, COALESCE(saida, NOW())) AS minutos FROM estacionamento WHERE DATE(entrada)=CURDATE() ORDER BY entrada DESC LIMIT 250");
            try {
                ResultSet rs = ps.executeQuery();
                try { while (rs.next()) out.add(fromRs(rs)); } finally { rs.close(); }
            } finally { ps.close(); }
            return out;
        });
    }

    private Resumo buscarResumo() throws SQLException {
        return db.executeWithRetry(conn -> {
            Resumo r = new Resumo();
            Statement st = conn.createStatement();
            try {
                ResultSet rs = st.executeQuery("SELECT " +
                        "SUM(CASE WHEN status='ABERTO' THEN 1 ELSE 0 END) abertos," +
                        "SUM(CASE WHEN DATE(entrada)=CURDATE() THEN 1 ELSE 0 END) entradas_hoje," +
                        "SUM(CASE WHEN status='FECHADO' AND DATE(saida)=CURDATE() THEN valor_total ELSE 0 END) faturamento_hoje " +
                        "FROM estacionamento");
                try {
                    if (rs.next()) {
                        r.abertos = rs.getInt("abertos");
                        r.entradasHoje = rs.getInt("entradas_hoje");
                        r.faturamentoHoje = rs.getDouble("faturamento_hoje");
                    }
                } finally { rs.close(); }
            } finally { st.close(); }
            return r;
        });
    }

    private void renderizarLista(ArrayList<Registro> dados, Resumo resumo, String titulo) {
        tvResumo.setText("Abertos: " + resumo.abertos + "   |   Entradas hoje: " + resumo.entradasHoje + "   |   Faturamento hoje: R$ " + money.format(resumo.faturamentoHoje));
        listaContainer.removeAllViews();
        TextView label = texto(titulo + " (" + dados.size() + ")", 14, true, hint);
        label.setPadding(0, 0, 0, dp(6));
        listaContainer.addView(label);

        if (dados.isEmpty()) {
            TextView vazio = texto("Nenhum veículo encontrado.", 16, false, hint);
            vazio.setGravity(Gravity.CENTER);
            vazio.setPadding(0, dp(25), 0, dp(25));
            listaContainer.addView(vazio, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (Registro r : dados) listaContainer.addView(cardRegistro(r));
    }

    private View cardRegistro(Registro r) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(r.id == veiculoSelecionadoId ? Color.parseColor("#1E3A5F") : card);
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(7));
        c.setLayoutParams(lp);

        LinearLayout linha1 = new LinearLayout(this);
        linha1.setOrientation(LinearLayout.HORIZONTAL);
        TextView placa = texto(r.placa, 21, true, cyan);
        TextView status = texto(r.status, 13, true, STATUS_ABERTO.equals(r.status) ? warning : success);
        status.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        linha1.addView(placa, new LinearLayout.LayoutParams(0, -2, 1));
        linha1.addView(status, new LinearLayout.LayoutParams(dp(110), -2));
        c.addView(linha1);

        String desc = "Ticket: " + r.ticket + "   Vaga: " + val(r.vaga) + "   Tipo: " + val(r.tipo);
        c.addView(texto(desc, 13, false, text));
        c.addView(texto("Veículo: " + val(r.veiculo) + "   Condutor: " + val(r.condutor), 13, false, hint));
        c.addView(texto("Entrada: " + val(r.entrada) + "   Permanência: " + formatTempo(r.minutos), 13, false, hint));
        c.addView(texto("Valor/hora: R$ " + money.format(r.valorHora) + "   Previsto: R$ " + money.format(calcularTotal(r.minutos, r.valorHora)), 13, true, success));
        if (r.observacao != null && r.observacao.trim().length() > 0) c.addView(texto("Obs: " + r.observacao, 12, false, hint));

        c.setOnClickListener(v -> {
            veiculoSelecionadoId = r.id;
            veiculoSelecionadoPlaca = r.placa;
            Toast.makeText(this, "Selecionado: " + r.placa, Toast.LENGTH_SHORT).show();
            carregarLista();
        });
        c.setOnLongClickListener(v -> { mostrarDetalhes(r); return true; });
        return c;
    }

    private void finalizarSelecionado() {
        if (veiculoSelecionadoId <= 0) {
            showError("Selecione um veículo na lista antes de finalizar a saída.");
            return;
        }
        showConfirm("Enviar para pagamento", "Enviar o ticket do veículo " + veiculoSelecionadoPlaca + " para a tela de forma de pagamento?\n\nO veículo só será baixado como entregue/fechado depois que o pagamento for finalizado.", () -> {
            showLoading("Preparando pagamento do estacionamento...");
            new Thread(() -> {
                try {
                    Registro r = buscarRegistroPorId(veiculoSelecionadoId);
                    if (r == null || !STATUS_ABERTO.equalsIgnoreCase(r.status)) {
                        hideLoading();
                        showError("Este veículo não está mais em aberto.");
                        return;
                    }
                    int minutos = r.minutos;
                    double total = calcularTotal(minutos, r.valorHora);
                    runOnUiThread(() -> {
                        hideLoading();
                        Intent intent = new Intent(this, PagamentoActivity.class);
                        intent.putExtra("is_estacionamento", true);
                        intent.putExtra("estacionamento_id", r.id);
                        intent.putExtra("estacionamento_ticket", r.ticket);
                        intent.putExtra("estacionamento_placa", r.placa);
                        intent.putExtra("estacionamento_veiculo", val(r.veiculo));
                        intent.putExtra("estacionamento_condutor", val(r.condutor));
                        intent.putExtra("estacionamento_telefone", val(r.telefone));
                        intent.putExtra("estacionamento_vaga", val(r.vaga));
                        intent.putExtra("estacionamento_tipo", val(r.tipo));
                        intent.putExtra("estacionamento_entrada", val(r.entrada));
                        intent.putExtra("estacionamento_minutos", minutos);
                        intent.putExtra("estacionamento_valor_hora", r.valorHora);
                        intent.putExtra("total_bruto", total);
                        intent.putExtra("total_liquido", total);
                        intent.putExtra("desconto", 0.0);
                        intent.putExtra("acrescimo", 0.0);
                        intent.putExtra("observacao", "Estacionamento - Ticket " + r.ticket + " - Placa " + r.placa);
                        intent.putExtra("num_itens", 1);
                        intent.putExtra("item_produto_id_0", 0);
                        intent.putExtra("item_descricao_0", "ESTACIONAMENTO TICKET " + r.ticket + " PLACA " + r.placa + " - " + formatTempo(minutos));
                        intent.putExtra("item_qtd_0", 1.0);
                        intent.putExtra("item_preco_0", total);
                        intent.putExtra("item_total_0", total);
                        startActivity(intent);
                    });
                } catch (Exception e) {
                    hideLoading();
                    showError("Erro ao preparar pagamento: " + e.getMessage());
                }
            }).start();
        });
    }

    private void cancelarSelecionado() {
        if (veiculoSelecionadoId <= 0) { showError("Selecione um veículo na lista antes de cancelar."); return; }
        showConfirm("Cancelar entrada", "Cancelar o registro do veículo " + veiculoSelecionadoPlaca + "?", () -> {
            showLoading("Cancelando registro...");
            new Thread(() -> {
                try {
                    db.executeWithRetryVoid(conn -> {
                        PreparedStatement ps = conn.prepareStatement("UPDATE estacionamento SET status='CANCELADO', saida=NOW(), valor_total=0, atualizado_em=NOW() WHERE id=? AND status='ABERTO'");
                        try { ps.setInt(1, veiculoSelecionadoId); ps.executeUpdate(); } finally { ps.close(); }
                    });
                    runOnUiThread(() -> { hideLoading(); showSuccess("Registro cancelado com sucesso."); veiculoSelecionadoId = -1; veiculoSelecionadoPlaca = ""; carregarLista(); });
                } catch (Exception e) { hideLoading(); showError("Erro ao cancelar: " + e.getMessage()); }
            }).start();
        });
    }



    private Registro buscarRegistroPorId(int id) throws SQLException {
        return db.executeWithRetry(conn -> {
            PreparedStatement ps = conn.prepareStatement("SELECT *, TIMESTAMPDIFF(MINUTE, entrada, NOW()) AS minutos FROM estacionamento WHERE id=? LIMIT 1");
            try {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                try {
                    if (rs.next()) return fromRs(rs);
                    return null;
                } finally { rs.close(); }
            } finally { ps.close(); }
        });
    }

    private void imprimirComprovanteChegada(String ticket, String placa, String veiculo, String condutor, String telefone, String vaga, String tipo, double valorHora, String obs) {
        new Thread(() -> {
            try {
                PrinterManager pm = new PrinterManager(this);
                String texto = gerarComprovanteChegada(ticket, placa, veiculo, condutor, telefone, vaga, tipo, valorHora, obs);
                boolean ok = pm.imprimirTexto(texto);
                runOnUiThread(() -> Toast.makeText(this, ok ? "Comprovante de chegada impresso." : "Entrada registrada. Impressora não configurada ou falhou.", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Entrada registrada. Falha ao imprimir chegada: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String gerarComprovanteChegada(String ticket, String placa, String veiculo, String condutor, String telefone, String vaga, String tipo, double valorHora, String obs) {
        String data = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
        String linha = "--------------------------------\n";
        StringBuilder sb = new StringBuilder();
        sb.append("<b>COMPROVANTE DE CHEGADA</b>\n");
        sb.append("ESTACIONAMENTO\n");
        sb.append(linha);
        sb.append("Ticket: ").append(ticket).append("\n");
        sb.append("Entrada: ").append(data).append("\n");
        sb.append("Placa: <b>").append(placa).append("</b>\n");
        sb.append("Veiculo: ").append(val(veiculo)).append("\n");
        sb.append("Tipo: ").append(val(tipo)).append("\n");
        sb.append("Vaga: ").append(val(vaga)).append("\n");
        sb.append("Condutor: ").append(val(condutor)).append("\n");
        sb.append("Telefone: ").append(val(telefone)).append("\n");
        sb.append("Valor/hora: R$ ").append(money.format(valorHora)).append("\n");
        if (obs != null && obs.trim().length() > 0) sb.append("Obs: ").append(obs.trim()).append("\n");
        sb.append(linha);
        sb.append("Guarde este comprovante.\n");
        sb.append("Apresente-o na retirada do veiculo.\n\n\n");
        return sb.toString();
    }

    private void abrirLeitorInteligentePlaca(EditText destino) {
        campoPlacaLeitorAtivo = destino;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView titulo = texto("Leitor inteligente de placa", 17, true, Color.DKGRAY);
        TextView msg = texto("Use a voz para informar a placa sem digitar. O sistema limpa espaços, traços, acentos, palavras como zero/um/dois e tenta montar a placa no padrão antigo ou Mercosul. A digitação manual continua disponível.", 13, false, Color.DKGRAY);
        Button btnVoz = botao("🎤 LER / FALAR PLACA", success, Color.BLACK);
        Button btnNormalizar = botao("✓ CORRIGIR PLACA DIGITADA", cyan, Color.BLACK);

        box.addView(titulo);
        box.addView(msg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, dp(10), 0, dp(5));
        box.addView(btnVoz, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-1, dp(50));
        lp2.setMargins(0, dp(4), 0, dp(4));
        box.addView(btnNormalizar, lp2);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setView(box)
                .setNegativeButton("Fechar", null)
                .create();

        btnVoz.setOnClickListener(v -> {
            dlg.dismiss();
            iniciarReconhecimentoVozPlaca();
        });
        btnNormalizar.setOnClickListener(v -> {
            String normalizada = normalizarPlacaInteligente(destino.getText().toString());
            destino.setText(normalizada);
            destino.setSelection(destino.getText().length());
            Toast.makeText(this, "Placa ajustada: " + normalizada, Toast.LENGTH_SHORT).show();
        });
        dlg.show();
    }

    private void iniciarReconhecimentoVozPlaca() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale a placa. Exemplo: ABC 1 D 23 ou ABC 1234");
            startActivityForResult(intent, REQ_DITAR_PLACA_ESTACIONAMENTO);
        } catch (ActivityNotFoundException e) {
            showError("Este aparelho não possui reconhecimento de voz disponível. A digitação manual continua liberada.");
        } catch (Exception e) {
            showError("Não foi possível abrir o leitor inteligente: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DITAR_PLACA_ESTACIONAMENTO && resultCode == RESULT_OK && data != null) {
            ArrayList<String> resultados = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (resultados == null || resultados.isEmpty()) {
                showError("Nenhuma placa foi reconhecida.");
                return;
            }
            String melhor = "";
            for (String r : resultados) {
                String n = normalizarPlacaInteligente(r);
                if (n.length() >= 6) { melhor = n; break; }
                if (n.length() > melhor.length()) melhor = n;
            }
            if (campoPlacaLeitorAtivo != null) {
                campoPlacaLeitorAtivo.setText(melhor);
                campoPlacaLeitorAtivo.setSelection(campoPlacaLeitorAtivo.getText().length());
            }
            Toast.makeText(this, "Placa reconhecida: " + melhor, Toast.LENGTH_LONG).show();
        }
    }

    private String normalizarPlacaInteligente(String bruto) {
        if (bruto == null) return "";
        String s = bruto.toUpperCase(Locale.ROOT)
                .replace("Á", "A").replace("À", "A").replace("Â", "A").replace("Ã", "A")
                .replace("É", "E").replace("Ê", "E")
                .replace("Í", "I")
                .replace("Ó", "O").replace("Ô", "O").replace("Õ", "O")
                .replace("Ú", "U")
                .replace("Ç", "C");

        s = s.replace("ZERO", "0").replace("UM", "1").replace("UMA", "1")
                .replace("DOIS", "2").replace("DUAS", "2")
                .replace("TRES", "3").replace("TRÊS", "3")
                .replace("QUATRO", "4").replace("CINCO", "5")
                .replace("SEIS", "6").replace("SETE", "7")
                .replace("OITO", "8").replace("NOVE", "9")
                .replace("TRACO", "").replace("TRAÇO", "").replace("HIFEN", "").replace("HÍFEN", "")
                .replace("PLACA", "");

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) out.append(c);
        }
        String limpo = out.toString();

        // Corrige confusões comuns de voz/OCR em placas brasileiras.
        limpo = limpo.replace(" ", "");
        if (limpo.length() > 7) {
            // tenta aproveitar a primeira sequência útil com 7 caracteres alfanuméricos
            limpo = limpo.substring(0, 7);
        }
        return limpo;
    }

    private void mostrarDetalhes(Registro r) {
        String msg = "Ticket: " + r.ticket +
                "\nPlaca: " + r.placa +
                "\nVeículo: " + val(r.veiculo) +
                "\nCondutor: " + val(r.condutor) +
                "\nTelefone: " + val(r.telefone) +
                "\nVaga: " + val(r.vaga) +
                "\nTipo: " + val(r.tipo) +
                "\nEntrada: " + val(r.entrada) +
                "\nSaída: " + val(r.saida) +
                "\nStatus: " + r.status +
                "\nPermanência: " + formatTempo(r.minutos) +
                "\nValor/hora: R$ " + money.format(r.valorHora) +
                "\nValor total: R$ " + money.format(STATUS_ABERTO.equals(r.status) ? calcularTotal(r.minutos, r.valorHora) : r.valorTotal) +
                "\nObservação: " + val(r.observacao);
        new AlertDialog.Builder(this).setTitle("Detalhes do estacionamento").setMessage(msg).setPositiveButton("OK", null).show();
    }

    private Registro fromRs(ResultSet rs) throws SQLException {
        Registro r = new Registro();
        r.id = rs.getInt("id"); r.ticket = rs.getString("ticket"); r.placa = rs.getString("placa");
        r.veiculo = rs.getString("veiculo"); r.condutor = rs.getString("condutor"); r.telefone = rs.getString("telefone");
        r.vaga = rs.getString("vaga"); r.tipo = rs.getString("tipo"); r.entrada = rs.getString("entrada"); r.saida = rs.getString("saida");
        r.status = rs.getString("status"); r.valorHora = rs.getDouble("valor_hora"); r.valorTotal = rs.getDouble("valor_total");
        r.observacao = rs.getString("observacao"); r.minutos = Math.max(0, rs.getInt("minutos"));
        return r;
    }

    private String gerarTicket() { return "EST" + new SimpleDateFormat("yyMMddHHmmss", Locale.ROOT).format(new Date()); }
    private double calcularTotal(int minutos, double valorHora) { int horas = Math.max(1, (int)Math.ceil(Math.max(1, minutos) / 60.0)); return horas * valorHora; }
    private String formatTempo(int min) { int h = min / 60; int m = min % 60; return h + "h " + m + "min"; }
    private String vazioNull(String s) { return s == null || s.trim().isEmpty() ? null : s.trim(); }
    private String val(String s) { return s == null || s.trim().isEmpty() ? "-" : s; }
    private double parseDouble(String s, double def) { try { return Double.parseDouble(s.replace(',', '.')); } catch(Exception e){ return def; } }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView texto(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setPadding(dp(2), dp(2), dp(2), dp(2));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private EditText campo(String hintText) { EditText e = new EditText(this); e.setHint(hintText); e.setHintTextColor(hint); e.setTextColor(text); e.setSingleLine(true); e.setPadding(dp(12), 0, dp(12), 0); e.setBackgroundColor(Color.parseColor("#1E2247")); return e; }
    private EditText campoDialog(String hintText) { EditText e = new EditText(this); e.setHint(hintText); e.setSingleLine(false); e.setPadding(dp(8), dp(8), dp(8), dp(8)); return e; }
    private Button botao(String s, int color, int txt) { Button b = new Button(this); b.setText(s); b.setTextColor(txt); b.setTextSize(12); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackgroundColor(color); b.setAllCaps(false); return b; }
    private LinearLayout.LayoutParams pesoLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1); lp.setMargins(dp(3), dp(3), dp(3), dp(3)); return lp; }

    private static class Registro { int id, minutos; String ticket, placa, veiculo, condutor, telefone, vaga, tipo, entrada, saida, status, observacao; double valorHora, valorTotal; }
    private static class Resumo { int abertos, entradasHoje; double faturamentoHoje; }
}
