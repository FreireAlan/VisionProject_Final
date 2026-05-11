# VisionProject — Atividades 1 a 4

Projeto Android Java + CameraX + OpenCV.

## Implementado

### Atividade 1
- Projeto Android em Java.
- CameraX com câmera traseira.
- Preview ao vivo via PreviewView.
- Botão para salvar frame JPEG em `Pictures/VisionProject`.

### Atividade 2
- Pipeline OpenCV: RGB → cinza → GaussianBlur → Canny.
- SeekBars para limiares Tlow/Thigh.
- Salvamento das 4 imagens: original, cinza, suavizada e bordas.

### Atividade 3
- Activity `ModeloCameraActivity`.
- Exibição de parâmetros K aproximados.
- Correção `Calib3d.undistort()` com `K` e `distCoeffs` fixos.
- Comparação original/corrigida.
- Canvas com seleção manual de até 5 pontos e linhas epipolares ilustrativas.

### Atividade 4
- Activity `CalibrationActivity`.
- Detecção de tabuleiro 9x6 cantos internos.
- Quadrado padrão: 25 mm.
- Captura automática quando a pose fica estável.
- Calibração com OpenCV `Calib3d.calibrateCamera`.
- Log/exibição de RMS, fx, fy, cx, cy, k1, k2, p1, p2, k3.
- Salvamento/carregamento de `calibration.json`.
- Before/after `undistort`.

## Como abrir

1. Abra o Android Studio.
2. Escolha `Open`.
3. Selecione a pasta `VisionProject`.
4. Aguarde o Gradle Sync.
5. Rode em dispositivo físico com câmera traseira.

## Observação

Para a Atividade 4, imprima um tabuleiro de calibração com 9x6 cantos internos e quadrados de 25 mm.
Cole o papel em uma superfície rígida para evitar ondulação.
