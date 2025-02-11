package com.bogdatech.utils;

import com.bogdatech.entity.TranslateTextDO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
                vocabulary.setTargetText(record.get("target_txte"));
//                System.out.println("target_text: " + record.get("target_txte"));
                vocabulary.setTargetCode(record.get("target_code"));
//                System.out.println("target_code: " + record.get("target_code"));
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
}
