package com.bogdatech.utils;

import com.bogdatech.entity.TranslateTextDO;
import com.bogdatech.model.controller.request.CsvRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class CsvUtils {

    public static List<TranslateTextDO> readCsv(String filePath) {
        List<TranslateTextDO> vocabularyList = new ArrayList<>();
        try {
            FileReader reader = new FileReader(filePath);

            Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
            Iterator<CSVRecord> iterator = records.iterator();
            if (iterator.hasNext()) {
                CSVRecord firstRecord = iterator.next();
                System.out.println("CSV Headers: " + firstRecord.toMap().keySet());
            }

//            Iterable<CSVRecord> records = CSVFormat.DEFAULT.withHeader().parse(reader);
            for (CSVRecord record : records) {
                TranslateTextDO vocabulary = new TranslateTextDO();
//                System.out.println("record: " + record);
                vocabulary.setSourceCode(record.get("source_code"));
                System.out.println("source_code: " + record.get("source_code"));
                vocabulary.setSourceText(record.get("source_text"));
                System.out.println("source_text: " + record.get("source_text"));
                vocabulary.setTargetText(record.get("target_text"));
                System.out.println("target_text: " + record.get("target_text"));
                vocabulary.setTargetCode(record.get("target_code"));
                System.out.println("target_code: " + record.get("target_code"));
                vocabularyList.add(vocabulary);
            }
        } catch (FileNotFoundException e) {
            System.out.println("错误原因： " + e.getMessage());
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return vocabularyList;
    }

    public static Map<String, String> readCsvToCsvRequest(String filePath) {
        Map<String, String> map = new HashMap<>();
        try {
            FileReader reader = new FileReader(filePath);
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
            Iterator<CSVRecord> iterator = records.iterator();
            if (iterator.hasNext()) {
                CSVRecord firstRecord = iterator.next();
//                System.out.println("CSV Headers: " + firstRecord.toMap().keySet());
            }
//            Iterable<CSVRecord> records = CSVFormat.DEFAULT.withHeader().parse(reader);
            for (CSVRecord record : records) {
                if (record.get("target_text") != null) {
                    map.put(record.get("source_text"), record.get("target_text"));
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("错误原因： " + e.getMessage());
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return map;
    }

    public static void writeCsv(List<CsvRequest> csvRequestList, String filePath) {
        // 创建一个 FileWriter 以便输出到文件
        try {
            try (FileWriter writer = new FileWriter(filePath);
                 // 创建 CSVPrinter 对象
                 CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("source_code", "source_text", "target_code", "target_text"))) {
//                 CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("code", "text"))) {
                // 遍历 List 并写入每一行
                for (CsvRequest csvRequest : csvRequestList) {
                    csvPrinter.printRecord(
                            csvRequest.getSource_code(),
                            csvRequest.getSource_text()
                            ,
                            csvRequest.getTarget_code(),
                            csvRequest.getTarget_text()
                    );
                }
                // 刷新和关闭打印流
                csvPrinter.flush();
                System.out.println("csv 存储成功");
            }
        } catch (IOException e) {
            System.out.println("错误原因： " + e.getMessage());
//            throw new RuntimeException(e);
        }
    }
}
