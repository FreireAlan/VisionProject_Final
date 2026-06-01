VisionProject — Patch da atividade de visão estéreo com smartphone

Como aplicar:
1) Feche o Android Studio.
2) Extraia este ZIP.
3) Copie a pasta 'app' extraída para dentro da raiz do seu projeto:
   D:\DOUTORADO\26-1\Disciplina Visao Computacional\VisionProject
4) Aceite substituir MainActivity.java e AndroidManifest.xml.
5) Abra o Android Studio e rode Sync Project with Gradle Files.
6) Execute o app no Redmi 14C.

Arquivos incluídos:
- MainActivity.java atualizado com os botões da nova atividade.
- StereoCaptureActivity.java: captura I_L.jpg e I_R.jpg.
- StereoProcessingActivity.java: ORB, matching, matriz fundamental, retificação e mapa de disparidade.
- StereoDepthActivity.java: profundidade Z e PLY.
- StereoUtils.java: utilitários do pipeline estéreo.
- AndroidManifest.xml atualizado com as novas Activities.

Observação:
O app usa a calibração existente em calibration.json quando ela estiver salva pela sua CalibrationActivity.
Se ela não existir, o pipeline usa uma K aproximada e distorção zero apenas para permitir teste inicial.
