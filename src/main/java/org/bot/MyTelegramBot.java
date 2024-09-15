package org.bot;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class MyTelegramBot extends TelegramLongPollingBot {

    // Clave de API de AEMET (reemplaza con tu propia clave)
    private static final String AEMET_API_KEY = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGVqYW5kcm8uZmVybmFuZGV6aGVycmVyby5iY2FAZ21haWwuY29tIiwianRpIjoiZTgwNjJlZTQtNjY1ZC00NzUwLWFhODYtN2NiMjM0ZmYzZDUyIiwiaXNzIjoiQUVNRVQiLCJpYXQiOjE3MjY0MzEzMDMsInVzZXJJZCI6ImU4MDYyZWU0LTY2NWQtNDc1MC1hYTg2LTdjYjIzNGZmM2Q1MiIsInJvbGUiOiIifQ.SesAsvGQmUkbyyyRVwAQXFxLiWe9Elc6v4FIz_OIhUE";
    private static final String AEMET_URL = "https://opendata.aemet.es/opendata/api/prediccion/especifica/municipio/diaria/33031/?api_key=";

    public static void main(String[] args) {
        // Inicializar la API de Telegram
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new MyTelegramBot()); // Registrar tu bot
            System.out.println("Bot iniciado con éxito.");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return "Impresora_termica_herrero_bot"; // Reemplaza con el nombre de usuario de tu bot
    }

    @Override
    public String getBotToken() {
        return "6891593089:AAG3wokV7Vjs9r_oAV-_NPl8XeNJYLmCKIE"; // Reemplaza con el token proporcionado por BotFather
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            // Verificar si es un mensaje de texto
            if (update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();
                System.out.println("Mensaje recibido: " + messageText);

                // Si el mensaje contiene "tiempo", obtenemos el clima de Langreo
                if (messageText.equalsIgnoreCase("tiempo")) {
                    String weatherInfo = getWeatherInfo();
                    printToThermalPrinter(weatherInfo); // Imprimir clima en la impresora
                    sendMessage(update, weatherInfo);   // Responder al usuario
                } else {
                    // Imprimir cualquier otro mensaje de texto en la impresora
                    printToThermalPrinter(messageText);
                    sendMessage(update, "Mensaje recibido: " + messageText);
                }
            }

            // Verificar si es una imagen
            if (update.getMessage().hasPhoto()) {
                // Obtener la foto de mayor resolución
                PhotoSize photo = update.getMessage().getPhoto().stream()
                        .max((p1, p2) -> Integer.compare(p1.getFileSize(), p2.getFileSize()))
                        .orElse(null);

                if (photo != null) {
                    try {
                        // Descargar la imagen desde Telegram
                        String filePath = getFilePath(photo.getFileId());
                        if (filePath != null) {
                            java.io.File downloadedImage = downloadImage(filePath);

                            // Imprimir la imagen
                            printImageToThermalPrinter(downloadedImage);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    // Metod para enviar un mensaje de respuesta al usuario
    private void sendMessage(Update update, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(update.getMessage().getChatId().toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Obtener la ruta del archivo desde el servidor de Telegram
    private String getFilePath(String fileId) throws TelegramApiException {
        GetFile getFileMethod = new GetFile();
        getFileMethod.setFileId(fileId);
        File file = execute(getFileMethod);
        return file.getFilePath();
    }

    // Descargar la imagen desde Telegram
    private java.io.File downloadImage(String filePath) throws IOException {
        String fileUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath;
        BufferedImage image = ImageIO.read(new URL(fileUrl));

        // Guardar la imagen descargada temporalmente
        java.io.File outputImage = java.io.File.createTempFile("image", ".jpg");
        ImageIO.write(image, "jpg", outputImage);
        return outputImage;
    }

    // Metod para imprimir texto en la impresora térmica
    private void printToThermalPrinter(String text) {
        try {
            ProcessBuilder pb = new ProcessBuilder("lp", "-d", "POS80P");
            pb.redirectInput(ProcessBuilder.Redirect.PIPE);
            Process process = pb.start();
            process.getOutputStream().write(text.getBytes());
            process.getOutputStream().flush();
            process.getOutputStream().close();
            process.waitFor();
            System.out.println("Texto impreso: " + text);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Metod para imprimir imagen en la impresora térmica
    private void printImageToThermalPrinter(java.io.File imageFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder("lp", "-d", "POS80P", imageFile.getAbsolutePath());
            Process process = pb.start();
            process.waitFor();
            System.out.println("Imagen impresa: " + imageFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Obtener información del clima en Langreo desde AEMET
    private String getWeatherInfo() {
        StringBuilder result = new StringBuilder();
        try {
            // Hacer la primera petición HTTP a la API de AEMET
            URL url = new URL(AEMET_URL + AEMET_API_KEY);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            // Verificar si la conexión es exitosa
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return "No se pudo obtener el clima. Código de respuesta: " + responseCode;
            } else {
                // Leer la respuesta de la API para obtener la URL con los datos reales
                Scanner scanner = new Scanner(url.openStream());
                while (scanner.hasNext()) {
                    result.append(scanner.nextLine());
                }
                scanner.close();
            }

            // Convertir la respuesta a un JSON
            String jsonResponse = result.toString();
            // Extraer la URL real de los datos (el campo "datos" en la respuesta)
            String dataUrl = extractDataUrl(jsonResponse);

            // Hacer una segunda petición a la URL de los datos reales
            URL dataUrlObj = new URL(dataUrl);
            HttpURLConnection dataConn = (HttpURLConnection) dataUrlObj.openConnection();
            dataConn.setRequestMethod("GET");
            dataConn.connect();

            // Leer la respuesta con los datos reales
            StringBuilder weatherData = new StringBuilder();
            Scanner dataScanner = new Scanner(dataUrlObj.openStream());
            while (dataScanner.hasNext()) {
                weatherData.append(dataScanner.nextLine());
            }
            dataScanner.close();

            // Procesar y devolver la información del clima
            return parseWeatherInfo(weatherData.toString());

        } catch (Exception e) {
            e.printStackTrace();
            return "Error obteniendo el clima.";
        }
    }

    // Metod para extraer la URL de datos del JSON de la primera respuesta
    private String extractDataUrl(String jsonResponse) {
        // Parsear el JSON
        JSONObject jsonObject = new JSONObject(jsonResponse);
        // Extraer el valor del campo "datos"
        return jsonObject.getString("datos");
    }

    private String parseWeatherInfo(String weatherJson) {
        StringBuilder result = new StringBuilder();

        try {
            // Convertir el JSON en un objeto JSONArray
            JSONArray jsonArray = new JSONArray(weatherJson);

            // Obtener el primer objeto del array (asumiendo que siempre hay al menos un objeto)
            JSONObject jsonObject = jsonArray.getJSONObject(0);

            // Obtener la predicción
            JSONObject prediccion = jsonObject.getJSONObject("prediccion");
            JSONArray dias = prediccion.getJSONArray("dia");

            // Recorrer cada día
            for (int i = 0; i < dias.length(); i++) {
                JSONObject dia = dias.getJSONObject(i);

                // Extraer la fecha
                String fecha = dia.optString("fecha", "Fecha no disponible");

                // Extraer temperaturas
                JSONObject temperatura = dia.optJSONObject("temperatura");
                int tempMax = temperatura != null ? temperatura.optInt("maxima", -1) : -1;
                int tempMin = temperatura != null ? temperatura.optInt("minima", -1) : -1;

                // Extraer estado del cielo
                JSONArray estadoCielo = dia.optJSONArray("estadoCielo");
                String descripcionEstado = "";
                if (estadoCielo != null) {
                    for (int j = 0; j < estadoCielo.length(); j++) {
                        JSONObject estado = estadoCielo.optJSONObject(j);
                        if (estado != null && "12-24".equals(estado.optString("periodo"))) {
                            descripcionEstado = estado.optString("descripcion", "Descripción no disponible");
                        }
                    }
                }

                // Construir el resultado para este día
                result.append(String.format("Fecha: %s\nTemperatura máxima: %d°C\nTemperatura mínima: %d°C\nEstado del cielo: %s\n\n",
                        fecha, tempMax != -1 ? tempMax : 0, tempMin != -1 ? tempMin : 0, descripcionEstado));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Error procesando la información del clima.";
        }

        return result.toString();
    }



}
