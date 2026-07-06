# -*- coding: utf-8 -*-
"""Gera os icones pdvpro.ico e print_server.ico (executado uma vez)."""
from PIL import Image, ImageDraw, ImageFont


def _fonte(size):
    for fp in ("/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
               "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
               "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(fp, size)
        except Exception:
            continue
    return ImageFont.load_default()


def _rounded(draw, box, radius, fill):
    draw.rounded_rectangle(box, radius=radius, fill=fill)


def _centralizar(draw, cx, cy, texto, fonte, fill):
    bb = draw.textbbox((0, 0), texto, font=fonte)
    w = bb[2] - bb[0]
    h = bb[3] - bb[1]
    draw.text((cx - w / 2 - bb[0], cy - h / 2 - bb[1]), texto, font=fonte, fill=fill)


def gerar_pdvpro(path):
    """Icone do PDV: fundo azul/ciano com um carrinho + texto PDV."""
    S = 256
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Fundo arredondado (gradiente simples azul -> ciano)
    _rounded(d, (8, 8, S - 8, S - 8), 46, fill=(13, 27, 62, 255))
    _rounded(d, (8, 8, S - 8, S - 8), 46, fill=None)
    # Faixa superior ciano
    _rounded(d, (8, 8, S - 8, 96), 46, fill=(41, 121, 255, 255))
    d.rectangle((8, 60, S - 8, 96), fill=(41, 121, 255, 255))
    # Carrinho de compras (desenho simples)
    verde = (0, 245, 160, 255)
    branco = (255, 255, 255, 255)
    # cesto
    d.line((70, 120, 96, 120), fill=branco, width=10)      # cabo
    d.line((96, 120, 108, 170), fill=branco, width=10)
    d.polygon([(104, 138), (196, 138), (186, 178), (116, 178)],
              outline=branco, width=8)
    # rodas
    d.ellipse((118, 188, 138, 208), fill=verde)
    d.ellipse((168, 188, 188, 208), fill=verde)
    # Texto PDV
    _centralizar(d, S / 2, 48, "PDV", _fonte(52), branco)
    # Salvar multiplos tamanhos
    img.save(path, sizes=[(16, 16), (24, 24), (32, 32), (48, 48),
                          (64, 64), (128, 128), (256, 256)])
    print("gerado:", path)


def gerar_print_server(path):
    """Icone do servidor de impressao: fundo laranja com uma impressora."""
    S = 256
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    _rounded(d, (8, 8, S - 8, S - 8), 46, fill=(38, 24, 8, 255))
    _rounded(d, (8, 8, S - 8, 96), 46, fill=(255, 109, 0, 255))
    d.rectangle((8, 60, S - 8, 96), fill=(255, 109, 0, 255))
    branco = (255, 255, 255, 255)
    cinza = (210, 216, 230, 255)
    verde = (0, 245, 160, 255)
    # Corpo da impressora
    _rounded(d, (58, 120, 198, 188), 12, fill=cinza)
    # Papel saindo embaixo
    d.rectangle((84, 176, 172, 224), fill=branco)
    d.line((96, 194, 160, 194), fill=(120, 130, 150, 255), width=5)
    d.line((96, 208, 160, 208), fill=(120, 130, 150, 255), width=5)
    # Papel/topo entrando
    d.rectangle((84, 96, 172, 122), fill=branco)
    # Luz verde (online)
    d.ellipse((172, 132, 188, 148), fill=verde)
    # Texto
    _centralizar(d, S / 2, 48, "PRINT", _fonte(40), branco)
    img.save(path, sizes=[(16, 16), (24, 24), (32, 32), (48, 48),
                          (64, 64), (128, 128), (256, 256)])
    print("gerado:", path)


if __name__ == "__main__":
    gerar_pdvpro("pdvpro.ico")
    gerar_print_server("print_server.ico")
