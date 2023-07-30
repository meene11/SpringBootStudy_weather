package zerobase.weather.service;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import zerobase.weather.WeatherApplication;
import zerobase.weather.domain.DateWeather;
import zerobase.weather.domain.Diary;
import zerobase.weather.error.InvalidDate;
import zerobase.weather.repository.DateWeatherRepository;
import zerobase.weather.repository.DiaryRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DiaryService {

    @Value("${openweathermap.key}")
    private String apiKey;

    private final DiaryRepository diaryRepository;
    private final DateWeatherRepository dateWeatherRepository;

    private  static final Logger logger = LoggerFactory.getLogger(WeatherApplication.class);


    public DiaryService(DiaryRepository diaryRepository, DateWeatherRepository dateWeatherRepository) {
        this.diaryRepository = diaryRepository;
        this.dateWeatherRepository = dateWeatherRepository;
    }

    @Transactional
    @Scheduled(cron = "0 0 1 * * *")
//    @Scheduled(cron = "0/5 * * * * *") //5초마다 테스트위해사용
    public void saveWeatherDate(){
        dateWeatherRepository.save(getWeatherFromApi());
    }


    @Transactional(readOnly = false, isolation = Isolation.SERIALIZABLE)
    public void createDiary(LocalDate date, String text){
        logger.info("started to create diary");

        // 날씨 데이터 가져오기 (API 에서 가져오기 or DB 에서 가져오기?)
        DateWeather dateWeather = getDateWeather(date);


        /*
        // 1) open weather map 에서 날씨 데이터 가져오기
        String weatherData = getWeatherString();

        // 2) 받아온 날씨 데이터 json 파싱하기
        Map<String, Object> parseWeather = parseWeather(weatherData);
        System.out.println("parseWeather : " + parseWeather);
        // 3) 파싱된 데이터 + 일기 값 우리DB에 넣기(저장)
        */

        // DB에 넣기
        Diary nowDiary = new Diary();
        nowDiary.setDateWeather(dateWeather);
        /*
        nowDiary.setWeather(parseWeather.get("main").toString());
        nowDiary.setIcon(parseWeather.get("icon").toString());
        nowDiary.setTemperature((Double) parseWeather.get("temp"));
        */
        nowDiary.setText(text);
        nowDiary.setDate(date);

        diaryRepository.save(nowDiary);

        logger.info("end to create diary");
    }

    private DateWeather getDateWeather(LocalDate date) {
        List<DateWeather> dateWeatherListFromDB = dateWeatherRepository.findAllByDate(date);
        if(dateWeatherListFromDB.size() == 0){
            // 새로 api  에서 날씨 정보를 가져와야한다
            // 정책상, 현재 날씨를 가져오도록 하거나, 날씨없이 일기를 쓰도록
            return getWeatherFromApi();
        } else {
            return dateWeatherListFromDB.get(0);
        }
    }


    private DateWeather getWeatherFromApi(){
        // 1) open weather map 에서 날씨 데이터 가져오기
        String weatherData = getWeatherString();
        // 2) 받아온 날씨 데이터 json 파싱하기
        Map<String, Object> parseWeather = parseWeather(weatherData);
        DateWeather dateWeather = new DateWeather();
        dateWeather.setDate(LocalDate.now());
        dateWeather.setWeather(parseWeather.get("main").toString());
        dateWeather.setIcon(parseWeather.get("icon").toString());
        dateWeather.setTemperature(Double.parseDouble(parseWeather.get("temp").toString()));

        return  dateWeather;
    }

    @Transactional(readOnly = true)
    public List<Diary> readDiary(LocalDate date) {
        if(date.isAfter(LocalDate.ofYearDay(3050, 1))){
            throw new InvalidDate();
        }
        logger.debug("read diary");
        return diaryRepository.findAllByDate(date);
    }

    public List<Diary> readDiaries(LocalDate startDate, LocalDate endDate) {
        return diaryRepository.findAllByDateBetween(startDate, endDate);
    }

    public void updateDiary(LocalDate date, String text) {
        Diary nowDiary = diaryRepository.getFirstByDate(date);
        nowDiary.setText(text);
        diaryRepository.save(nowDiary); //DB에 새로운 row 생성으로 저장이 아니라 기존의 꺼에 덮어씌어진다. id로 해당 row찾아서 수정된text만 변경
    }

    public void deleteDiary(LocalDate date) {
        diaryRepository.deleteAllByDate(date);
    }

    private String getWeatherString(){
        String apiUrl = "https://api.openweathermap.org/data/2.5/weather?q=seoul&appid="+apiKey;
        System.out.println(apiUrl);

        try{
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            BufferedReader br;
            if(responseCode == 200){
                br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            } else {
                br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }
            String inputLine;
            StringBuilder response = new StringBuilder();
            while((inputLine = br.readLine()) != null){
                response.append(inputLine);
            }
            br.close();
            return response.toString();

        } catch (Exception e) {
            return "failed to get response";
        }

    }

    /*
    getWeatherString() 이용해 weathe api 로 날씨 데이터를 string 형태로 받아와서
    JSONParser 를 이용해 파싱해주고
    Map<String, Object> 타입으로 원하는 value를 담아서 반환해줌
     */
    private Map<String, Object> parseWeather(String jsonString){
        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObject;

        try {
            jsonObject = (JSONObject) jsonParser.parse(jsonString);
            System.out.println( " jsonObject :: " + jsonObject);
        }catch (ParseException e){
            throw  new RuntimeException(e);
        }

        Map<String, Object> resultMap = new HashMap<>();
        JSONObject mainData = (JSONObject) jsonObject.get("main");
        resultMap.put("temp", mainData.get("temp"));
        JSONArray weatherArray = (JSONArray) jsonObject.get("weather");
        JSONObject weatherData = (JSONObject) weatherArray.get(0);
        resultMap.put("main", weatherData.get("main"));
        resultMap.put("icon", weatherData.get("icon"));

        // open api 에서 weather 은 main과 달리 배열로 담겨져 있어서 JSONArray로 받아야 오류가 안남
        /*
        JSONArray rowdata = (JSONArray) jsonObject.get("weather");
        System.out.println( " rowdata 22 :: " + rowdata);
        JSONObject weatherData = (JSONObject) rowdata.get(0);
        resultMap.put("main", weatherData.get("main"));
        resultMap.put("icon", weatherData.get("icon"));

        System.out.println( " resultMap 22 main :: " + resultMap.get("main"));
        System.out.println( " resultMap 22 icon :: " + resultMap.get("icon"));
        */
        /*
        JSONObject weatherData = (JSONObject) jsonObject.get("weather");
        System.out.println( " weatherData 22 :: " + weatherData);
        resultMap.put("main", weatherData.get("main"));
        resultMap.put("icon", weatherData.get("icon"));

        System.out.println( " resultMap 22 main :: " + resultMap.get("main"));
        System.out.println( " resultMap 22 icon :: " + resultMap.get("icon"));
        */

        return resultMap;

    }



}
