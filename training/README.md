# FOCOS — Contagem de objetos sobre chão ou mesa

## Objetivo
Treinar YOLOv8n com **uma classe: objeto**. Não é classificação de marcas/modelos; a contagem corresponde às caixas detectadas. Fotos de catálogos, marcas e caminhões inteiros não são exemplos adequados de objetos espalhados sobre superfícies.

## Fontes candidatas verificadas (não baixadas)
- [Assembly parts](https://universe.roboflow.com/mariac-dfxc4/assembly-parts) — licença MIT indicada na página.
- [screw-nut-washer](https://universe.roboflow.com/annotation-svf3l/screw-nut-washer) — CC BY 4.0.
- [Screw detection & classification](https://universe.roboflow.com/corrosion-a1pkl/screw-detection-classification) — CC BY 4.0.
- [Screw - Nut - Washer](https://universe.roboflow.com/alexiss-workspace-fznnn/screw-nut-washer-zyrsr) — CC BY 4.0.
- [detectIndustrialParts](https://universe.roboflow.com/aarhus-university/detectindustrialparts) — CC BY 4.0, revisar classes para não rotular mesas e caixas como itens.

**Não afirmar que os dados foram baixados ou treinados.** Verifique licenças e atribuição de cada versão antes de usar. Evite misturar conjuntos com anotação incompleta, pois itens sem caixa podem virar falsos negativos.

## Preparação
Exporte datasets em formato YOLO detection, copie cada exportação em `training/raw/NOME_DO_CONJUNTO/` com `images/train`, `labels/train`, `images/valid` e `labels/valid` (ou `val`). A ferramenta `prepare_dataset.py` converte IDs de classe para 0, separando por conjunto e preservando as caixas; só use conjuntos cujas classes representam objetos físicos individuais.

```bash
python training/prepare_dataset.py
pip install ultralytics
yolo detect train model=yolov8n.pt data=training/data.yaml imgsz=640 epochs=100
yolo export model=runs/detect/train/weights/best.pt format=onnx imgsz=640 opset=12 simplify=True nms=False
```

Copie `best.onnx` para `app/src/main/assets/parts_yolov8.onnx`. Entrada esperada: [1,3,640,640]; saída [1,5,N]. Valide com fotos independentes de mesas/chãos, iluminação variada, negativos e peças encostadas, medindo erro absoluto da contagem e falsos positivos.

## Situação
Integração Android de inferência ONNX adicionada. **Não há pesos treinados, testes em aparelho ou APK YOLOv8 validado**. Publicação automática bloqueada. YOLOv8 usa AGPL-3.0; avalie as obrigações ou licença comercial antes de uso empresarial.
