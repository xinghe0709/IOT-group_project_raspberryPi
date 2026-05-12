package org.xinghe.AIThermalGuardIoT.infrastructure.file;



import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xinghe.AIThermalGuardIoT.common.exception.BusinessException;
import org.xinghe.AIThermalGuardIoT.common.exception.ErrorCode;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Service
/**
 * 文档解析服务实现。
 * <p>
 * 基于 Apache Tika 统一解析上传文件、字节数组或输入流中的文本内容，
 * 并在解析后执行文本清洗，供简历解析、知识库入库等场景复用。
 * </p>
 */
public class DocumentParseService{

    private static final int MAX_TEXT_LENGTH = 5 * 1024 * 1024;
    /**
     * 构造文档解析服务。
     *
     * @param textCleaningService 文本清洗服务
     */
    public DocumentParseService(TextCleaningService textCleaningService) {
        this.textCleaningService = textCleaningService;
    }

    private final TextCleaningService textCleaningService;

    /**
     * 解析上传文件中的文本内容。
     * <p>
     * 当文件为空时直接返回空字符串；解析成功后会进行文本清洗。
     * </p>
     *
     * @param file 上传文件
     * @return 清洗后的文本内容
     * @throws BusinessException 当解析失败时抛出
     */

    public String parseContent(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        log.info("开始解析文件:{}", fileName);
        if(file.isEmpty() || file.getSize() == 0){
            log.warn("解析失败：{}", fileName);
            return "";
        }

        try(InputStream inputStream = file.getInputStream()){
            String content = parseContent(inputStream);
            String cleanedContent = textCleaningService.cleanText(content);
            log.info("Parsing file is success,the extracted length is {}", content.length());
            return cleanedContent;
        }catch (Exception e){
            log.error("Parsing is failed:{}", e.getMessage(),e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Parsing is failed: " + e.getMessage());
        }


    }

    /**
     * 解析文件字节数组中的文本内容。
     *
     * @param fileBytes 文件字节数组
     * @param fileName 原始文件名（用于日志）
     * @return 清洗后的文本内容
     * @throws BusinessException 当解析失败时抛出
     */

    public String parseContent(byte[] fileBytes, String fileName) {
        log.info("开始解析文件（从字节数组）：{}", fileBytes);

        if(fileBytes == null || fileBytes.length == 0){
            log.warn("byteArray is empty:{}", fileName);
        }

        try(InputStream inputStream = new ByteArrayInputStream(fileBytes)){
            String content = parseContent(inputStream);
            String cleanedContent = textCleaningService.cleanText(content);
            log.info("Parsing file is success,the extracted length is {}", content.length());
            return cleanedContent;
        }catch (Exception e){
            log.error("Parsing is failed:{}", e.getMessage(),e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Parsing is failed: " + e.getMessage());
        }
    }

    /**
     * 解析输入流中的文本内容。
     * <p>
     * 该方法使用 Tika 自动识别文件类型，禁用嵌入文档提取，并对 PDF 启用按位置排序，
     * 以提高正文提取的稳定性。
     * </p>
     *
     * @param inputStream 文件输入流
     * @return 提取出的原始文本内容
     * @throws IOException 输入输出异常
     * @throws TikaException Tika 解析异常
     * @throws SAXException SAX 解析异常
     */

    public String parseContent(InputStream inputStream) throws IOException, TikaException, SAXException {
        AutoDetectParser parser = new AutoDetectParser();

        //只提取正文
        BodyContentHandler handler = new BodyContentHandler();

        Metadata metadata = new Metadata();

        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        context.set(EmbeddedDocumentExtractor.class, new NoOpEmbeddedDocumentExtractor());

        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setSortByPosition(true);
        pdfConfig.setExtractInlineImages(false);
        context.set(PDFParserConfig.class, pdfConfig);

        parser.parse(inputStream,handler, metadata, context );


        return handler.toString();
    }

    /**
     * 从文件存储下载文件后解析其文本内容。
     *
     * @param storageService 文件存储服务
     * @param storageKey 存储键（用于错误日志定位）
     * @param originalFilename 原始文件名
     * @return 清洗后的文本内容
     * @throws BusinessException 当下载或解析失败时抛出
     */

    public String downloadAndParseContent(FileStorageService storageService, String storageKey, String originalFilename) {

        byte[] filesBytes = storageService.downloadFile(originalFilename);
        try {
            if(filesBytes == null || filesBytes.length == 0){
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Downloading fail");
            }
            return parseContent(filesBytes, originalFilename);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("下载并解析文件失败: storageKey={}, error={}", storageKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "下载并解析文件失败: " + e.getMessage());
        }

    }
}
