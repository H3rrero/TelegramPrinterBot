package org.bot;

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
import java.io.IOException;
import java.net.URL;

public class MyTelegramBot extends TelegramLongPollingBot {

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

                // Imprimir texto en la impresora térmica
                printToThermalPrinter(messageText);

                // Responder al usuario de Telegram
                SendMessage message = new SendMessage();
                message.setChatId(update.getMessage().getChatId().toString());
                message.setText("Mensaje recibido: " + messageText);
                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
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

    // Método para imprimir texto en la impresora térmica
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

    // Método para imprimir imagen en la impresora térmica
    private void printImageToThermalPrinter(java.io.File imageFile) {
        try {
            // Aquí podrías necesitar convertir la imagen a un formato compatible con tu impresora
            // Dependiendo de tu impresora, puedes necesitar convertir a blanco y negro o cambiar la resolución
            ProcessBuilder pb = new ProcessBuilder("lp", "-d", "POS80P", imageFile.getAbsolutePath());
            Process process = pb.start();
            process.waitFor();
            System.out.println("Imagen impresa: " + imageFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
