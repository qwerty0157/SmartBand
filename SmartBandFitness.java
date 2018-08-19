import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
 
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.fitness.Fitness;
import com.google.api.services.fitness.Fitness.Users;
import com.google.api.services.fitness.Fitness.Users.DataSources;
import com.google.api.services.fitness.Fitness.Users.DataSources.Datasets;
import com.google.api.services.fitness.FitnessScopes;
import com.google.api.services.fitness.model.DataPoint;
import com.google.api.services.fitness.model.DataSource;
import com.google.api.services.fitness.model.ListDataSourcesResponse;
import com.google.api.services.fitness.model.Value;
 
/**
 *  Google Fit APIで心拍データを取得するサンプルプログラム<br>
 * 
 *  <ul>
 *      <li>SONY SmartBand 2 SWR12を用いて心拍データの取得を行うとする場合、
 *      SmartBand 2アプリの設定でGoogleFitをONに設定すること。</li>
 * 
 *      <li>Google API Consoleで認証情報(OAuth 2.0 クライアントID)の取得を行うこと。<br>
 *          また、認証情報はJSON形式でダウンロードし、Javaの実行環境から見える適当な位置に配置すること。</li>
 * 
 *      <li>Google API Manager/API ConsoleでFitness APIを有効化しておくこと。</li>
 *  </ul>
 * 
 *  <dl>
 *      <dt>必要なライブラリ:</dt>
 *      <dd>API Client Library for Java   ( https://github.com/google/google-api-java-client )</dd>
 *      <dd>Fitness Client Library for Java ( https://developers.google.com/api-client-library/java/apis/fitness/v1 )</dd>
 *  </dl>
 *
 *  <br>
 *  <dl>
 *      <dt>Lisense:</dt>
 *      <dd>This software is distributed under the license of NYSL.<br>
 *          ( http://www.kmonos.net/nysl/ )</dd>
 *  </dl>
 * 
 * @author makoto
 *
 */
public class GoogleFitAPITest {
 
    /**
     * テストの実行
     * 
     * @param args
     */
    public static void main(String[] args) {
         
        try {
             
            GoogleFitAPITest test = new GoogleFitAPITest();
            Credential credential = test.authorize();
            test.build(credential);
             
            List<DataSource>sourceList = test.getDataSources();
            List<DataSource>heartRateSourceList = test.filter(sourceList, HEART_RATE_BPM); 
             
            for (DataSource source:heartRateSourceList) {
                 
                OffsetDateTime end = OffsetDateTime.now();
                OffsetDateTime start = end.minusDays(1);
                 
                test.showData(source.getDataStreamId(), start, end);
            }
             
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
 
    }
     
    /**
     * Google Fitのパブリックデータタイプで定義された心拍数
     */
    private static final String HEART_RATE_BPM = "com.google.heart_rate.bpm";
     
    /**
     * Google FitのユーザID
     */
    private static final String USER_ID = "me";
     
    /**
     * スレッドセーフなHTTPトランスポート
     */
    private HttpTransport httpTransport;
    /**
     * JSONを扱うためのファクトリ
     */
    private JsonFactory jsonFactory;
     
    /**
     * Google Fitサービスのインスタンス 
     */
    private Fitness fitness;
     
    /**
     *  GoogleのOAuth 2.0認証情報の格納先<br>
     *  《メモ》
     *      OSユーザのホームディレクトリ直下にディレクトリ「sony_sbr12_auth」が作成される
     */
    private File AUTH_STORE_PATH = new File(System.getProperty("user.home"), "sony_sbr12_auth");
     
     
     
    /**
     * コンストラクタ
     * 
     * @throws IOException
     * @throws GeneralSecurityException
     */
    private GoogleFitAPITest() throws IOException, GeneralSecurityException {
         
        httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        jsonFactory = JacksonFactory.getDefaultInstance();
    }
     
    /**
     * OAuth 2.0認証の実行<br>
     * 
     * @return
     * @throws IOException
     * @throws GeneralSecurityException
     */
    private Credential authorize() throws IOException, GeneralSecurityException {
         
        // Google API Consoleから取得したClient ID/Client Secretを読み込む
        InputStream in = GoogleFitAPITest.class.getResourceAsStream("/client_id.json");
        GoogleClientSecrets secrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(in));
         
        // GoogleのOAuth 2.0認証用のFlowを生成
        // 認証する権限は、Google Fitのすべての項目
        // 認証した結果はファイルに保存
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, secrets,
                FitnessScopes.all())
                .setDataStoreFactory(new FileDataStoreFactory(AUTH_STORE_PATH))
                .build();
         
        // 認証を行っていない、または無効な場合、ブラウザが立ち上がり認証を求められる
        //  《メモ》
        //  非GUI環境では実行できないようだ
        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
         
        return credential; 
    }
     
    /**
     * Google Fitサービスのインスタンス生成
     * 
     * @param credential    OAuth 2.0認証情報
     * @throws IOException
     */
    private void build(Credential credential) throws IOException {
         
        // 《メモ》
        // 最低限のパラメータでもアクセスが可能
        // 読み込みしか行っていないためか、Application Nameも不要だった
        fitness = new Fitness.Builder(httpTransport, jsonFactory, credential)
                .build();
    }
 
    /**
     * Google Fitに登録されているデータソースの取得
     * 
     * @return  
     * @throws IOException
     */
    private List<DataSource> getDataSources() throws IOException {
         
        DataSources sources = fitness.users().dataSources();
        Users.DataSources.List l = sources.list(USER_ID);
        ListDataSourcesResponse list = l.execute();
         
        return list.getDataSource();
         
    }
     
    /**
     * データソースのコレクションから指定したデータタイプに一致するリストの抽出
     * 
     * @param sourceList    Google Fitから取得したデータソース
     * @param dataType  データタイプ名(例: com.google.heart_rate.bpm)
     * @return
     */
    private List<DataSource>filter(List<DataSource>sourceList, String dataType) {
         
        List<DataSource>l = new ArrayList<>();
         
        for (DataSource source:sourceList) {
            if (source.getDataType().getName().equals(dataType)) {
                l.add(source);
            }
        }
         
        return l;
    }
     
    /**
     *  指定データソース・指定期間のデータを取得し、標準出力に表示する
     * 
     * @param dataSourceId  データソースID
     * @param start 開始日時
     * @param end   終了日時
     * @throws IOException
     */
    private void showData(String dataSourceId, OffsetDateTime start, OffsetDateTime end) throws IOException {
         
        // 開始日時・終了日時をナノ秒表記に変換
        String startText = String.valueOf(toNanos(start));
        String endText = String.valueOf(toNanos(end));
        // 開始日時・終了日時を組み合わせたものをデータセットIDとする
        String datasetId = startText + "-" + endText;
 
        System.out.println("<BOD:\t data-source-id[" + dataSourceId + "], dataset-id[" + datasetId + "]>");
         
        // Google Fitに問い合わせ
        Datasets.Get cmd = fitness.users().dataSources().datasets().get(USER_ID, dataSourceId, datasetId);
        com.google.api.services.fitness.model.Dataset dataset = cmd.execute();
         
        // 取得したデータを一覧表示
        for (DataPoint dp:dataset.getPoint()) {
            // ナノ秒をミリ秒に変換
            DateTime dt = new DateTime(toMilliseconds(dp.getStartTimeNanos()));
            // 有効なデータが存在しな場合は、ダミーデータを生成
            Value v = (dp.getValue().size() == 0) ? new Value() : dp.getValue().get(0);
             
            System.out.println(dt + "=>" + v.getFpVal());
        }
         
        System.out.println("<EOD:\t data-source-id[" + dataSourceId + "], dataset-id[" + datasetId + "]>");
    }
     
    /**
     *  OffsetDateTimeをナノ秒表現に変換
     * 
     * @param time
     * @return
     */
    private static long toNanos(OffsetDateTime time) {
        return (time.toEpochSecond() * 1000000000L) + time.getNano();
    }
     
    /**
     *  ナノ秒をミリ秒に変換
     * 
     * @param nanos
     * @return
     */
    private static long toMilliseconds(long nanos) {
        return nanos / 1000000L;
    }
}