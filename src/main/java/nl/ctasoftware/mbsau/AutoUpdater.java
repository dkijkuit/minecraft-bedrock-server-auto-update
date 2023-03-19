package nl.ctasoftware.mbsau;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
public class AutoUpdater {
    public static final String BINARY_DOWNLOAD_ENDPOINT = "https://www.minecraft.net/en-us/download/server/bedrock";
    public static final String BINARY_SERVER_ENDPOINT_PREFIX = "https://minecraft.azureedge.net/bin-linux/bedrock-server-";

    public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
        if(Objects.nonNull(args) && args.length == 2) {
            if(validateArgs(args)) {
                final String serverDir = args[0];
                final String backupDir = args[1];
                final String downloadedBinary = downloadBedrockServerBinary(backupDir).orElseThrow(() -> new RuntimeException("Unable to determine downloaded binary"));

                try {
                    final File createdBackupDir = installNewServerVersion(serverDir, backupDir, downloadedBinary);
                    restoreBackupConfigs(serverDir, createdBackupDir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            log.error("Invalid arguments: msbau <server_install_dir> <backup_dir>");
            log.error("\t<server_install_dir> -> directory where the current bedrock server is installed");
            log.error("\t<backup_dir> -> directory where the backups should be copied to");
        }
    }

    private static boolean validateArgs(final String args[]){
        boolean isValid = true;
        final File serverInstallDir = new File(args[0]);
        final File backupDir = new File(args[1]);

        if (!serverInstallDir.exists() || !serverInstallDir.isDirectory()) {
            log.error("Server install directory does not exist or is not a directory: {}", args[0]);
            isValid = false;
        }
        if (!backupDir.exists() || !backupDir.isDirectory()) {
            log.error("Backup directory does not exist or is not a directory: {}", args[1]);
            isValid = false;
        }

        return isValid;
    }

    private static File installNewServerVersion(final String serverDir, final String backupDir, final String downloadedBinary) throws IOException {
        final String dateTimeFormatted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"));
        final File serverBackupDir = new File(String.join(File.separator, backupDir, "backup_".concat(dateTimeFormatted)));
        log.info("Copy to backup dir: {}", serverBackupDir);

        FileUtils.copyDirectory(new File(serverDir), serverBackupDir);

        log.info("Unzipping to server install dir ...");

        unzipFile(downloadedBinary, new File(serverDir));

        return serverBackupDir;
    }

    private static void restoreBackupConfigs(final String serverDir, final File backupDir) throws IOException {
        log.info("Restoring config files ...");

        final File serverDirFile = new File(serverDir);
        FileUtils.copyFileToDirectory(new File(backupDir, "server.properties"), serverDirFile);
        FileUtils.copyFileToDirectory(new File(backupDir, "allowlist.json"), serverDirFile);
        FileUtils.copyFileToDirectory(new File(backupDir, "permissions.json"), serverDirFile);

        log.info("All done!");
    }

    private static Optional<String> downloadBedrockServerBinary(final String backupDir) throws URISyntaxException, IOException, InterruptedException {
        final HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(new URI(BINARY_DOWNLOAD_ENDPOINT)).GET().build();

        final HttpClient httpClient = HttpClient.newHttpClient();
        final HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        final int startIndex = response.body().indexOf(BINARY_SERVER_ENDPOINT_PREFIX);
        final int endIndex = response.body().indexOf(".zip", startIndex) + 4;

        final String downloadLink = response.body().substring(startIndex, endIndex);
        final String fileName = String.join(File.separator, backupDir, downloadLink.substring(downloadLink.lastIndexOf("/") + 1));

        log.info("Found download link: {}", downloadLink);
        log.info("Using file name: {}, downloading ...", fileName);

        final ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(downloadLink).openStream());
        try(FileOutputStream fileOutputStream = new FileOutputStream(fileName)) {
            fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            return Optional.of(fileName);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        return Optional.empty();
    }

    private static void unzipFile(final String zipFile, final File destDir) throws IOException {
        final byte[] buffer = new byte[8192];
        final ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));

        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            final File newFile = newFile(destDir, zipEntry);
            if (zipEntry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                // fix for Windows-created archives
                final File parent = newFile.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }

                // write file content
                final FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zipEntry = zis.getNextEntry();
        }

        zis.closeEntry();
        zis.close();
    }
    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        final File destFile = new File(destinationDir, zipEntry.getName());

        final String destDirPath = destinationDir.getCanonicalPath();
        final String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }
}
