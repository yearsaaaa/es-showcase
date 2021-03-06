package es.showcase.client.esnative;

import es.showcase.domain.Employee;
import es.showcase.util.JacksonUtils;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.highlight.HighlightField;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


/**
 * Employee demo application.
 */
public class EmployeeTest {

    private ESClient esClient;

    private Client client;

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeTest.class);

    @Before
    public void initESClient(){
        esClient = new ESTransportClient("192.168.8.247");
        client = esClient.getEsClient();
    }

    @Test
    public void testConEs(){
        NodesInfoResponse response =
                client.admin().cluster().nodesInfo(new NodesInfoRequest().timeout("10")).actionGet();
        Map<String,NodeInfo> nodesMap = response.getNodesMap();
        //打印节点信息
        for(Map.Entry<String, NodeInfo> entry : nodesMap.entrySet()){
            System.out.println(entry.getKey() + ":" + entry.getValue().getServiceAttributes()) ;
        }
    }

    /**
     * 单条数据索引
     * prepareIndex().setSource()只能传入单条数据文档
     * @throws Exception
     */
    @Test
    public void indexSingle() throws Exception {
        //index 1
        XContentBuilder builder = XContentFactory.jsonBuilder()
                .startObject()
                .field("id","HP-testcase")
                .field("name", "yearsaaaa")
                .field("sex", Employee.Sex.MAN)
                .field("age",20)
                .field("salary",1000)
                .field("birthday","1990-01-01")
                .field("descript","Hello World")
                .endObject();
        client.prepareIndex("company","employees","HP-0xff00")
                .setSource(builder.string())
                .execute().actionGet();

        //index 2
        String emp1 = JacksonUtils.obj2Json(Employee.CreateFakerEmp().get(0));
        String emp2 = JacksonUtils.obj2Json(Employee.CreateFakerEmp().get(1));
        String emp3 = JacksonUtils.obj2Json(Employee.CreateFakerEmp().get(2));
        String emp4 = JacksonUtils.obj2Json(Employee.CreateFakerEmp().get(3));
        String emp5 = JacksonUtils.obj2Json(Employee.CreateFakerEmp().get(4));
        client.prepareIndex("company","employees")
                .setSource(emp1)
                .execute().actionGet();
        client.prepareIndex("company","employees")
                .setSource(emp2)
                .execute().actionGet();
        client.prepareIndex("company","employees")
                .setSource(emp3)
                .execute().actionGet();
        client.prepareIndex("company","employees")
                .setSource(emp4)
                .execute().actionGet();
        client.prepareIndex("company","employees")
                .setSource(emp5)
                .execute().actionGet();
        LOG.info("All Document are indexd...");
        //getDocument by id
        GetResponse getResponse = client.prepareGet("company","employees","HP-0xff00")
                .execute().actionGet();
        Map<String,Object> source = getResponse.getSource();
        System.out.println("------------------------------");
        System.out.println("Index: " + getResponse.getIndex());
        System.out.println("Type: " + getResponse.getType());
        System.out.println("Id: " + getResponse.getId());
        System.out.println("Version: " + getResponse.getVersion());
        System.out.println(source);
        System.out.println("------------------------------");
    }

    /**
     * 批量创建索引
     * @throws Exception
     */
    @Test
    public void indexMultipleWithBulk() throws Exception {
        List<Employee> employees = Employee.CreateFakerEmp();
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        for(Employee employee : employees){
            IndexRequest indexRequest = client.prepareIndex("company","employees")
                    .setSource(JacksonUtils.obj2Json(employee))
                    .request();
            bulkRequest.add(indexRequest);
        }
        BulkResponse bulkResponse = bulkRequest.execute().actionGet();
        if (bulkResponse.hasFailures()) {
            LOG.error("批量创建索引错误！");
        }
    }

    @Test
    public void search(){
        SearchResponse searchResponse = client.prepareSearch("company")
                .setTypes("employees")
                .setSearchType(SearchType.QUERY_AND_FETCH)
                .setQuery(QueryBuilders.fieldQuery("descript", "easy"))
                .setFilter(FilterBuilders.rangeFilter("salary").from(6000).to(7000))
                .setFrom(0).setSize(20).setExplain(true)        //setSize分页显示,setExplain是否按查询匹配度排序
                .execute().actionGet();
        SearchHits searchHits = searchResponse.getHits();
        System.out.println("Current results: " + searchHits.totalHits());
        for (SearchHit hit : searchHits) {
            System.out.println("------------------------------");
            Map<String,Object> result = hit.getSource();
            System.out.println(result);
        }
    }

    /**
     * 高亮显示
     * @throws IOException
     */
    @Test
    public void searchWithHighlighted() throws IOException {
        SearchResponse searchResponse = client.prepareSearch("company")
                .setTypes("employees")
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setQuery(QueryBuilders.fieldQuery("descript", "的"))
                .addHighlightedField("descript")
                .setHighlighterPreTags("<span style=\"color:red\">")
                .setHighlighterPostTags("</span>")
                .setFilter(FilterBuilders.rangeFilter("age").from(25).to(30))
                .setFrom(0).setSize(20).setExplain(true)
                .execute().actionGet();
        SearchHits hits = searchResponse.getHits();
        System.out.println("Current results: " + hits.totalHits());
        for(SearchHit searchHit : hits){
            String json = searchHit.getSourceAsString();
            Employee employee = JacksonUtils.json2Obj(json, Employee.class);
            Map<String, HighlightField> result = searchHit.highlightFields();
            HighlightField titleField = result.get("descript");
            Text[] titleTexts =  titleField.fragments();
            String discript = "";
            for(Text text : titleTexts){
                discript += text;
            }
            employee.setDescript(discript);
            System.out.println(employee.getDescript());
        }
    }

    /**
     * QueryBuilders.matchAllQuery()
     * 匹配所有Document的Query
     * @throws IOException
     */
    @Test
    public void testMatchAllQuery() throws IOException {
        QueryBuilder builder = QueryBuilders.matchAllQuery();
        testQuery(builder);
    }

    /**
     * matchQuery根据field的值对Document进行查询
     * 可以看到默认情况下汉字通过matchQuery方法查询时会被分词器分词
     */
    @Test
    public void testMatchQuery() throws IOException {
        QueryBuilder builder1 = QueryBuilders.matchQuery("name","吕梓");
        testQuery(builder1);
        System.out.println("=====================");
        QueryBuilder builder2 = QueryBuilders.matchQuery("descript","抠脚大雪worry");
        testQuery(builder2);
    }

    /**
     * multiMatchQuery中可以指定多个field,是matchQuery的增强版
     * 汉字通过matchQuery方法查询时会被分词器分词
     */
    @Test
    public void testMultiMatchQuery() throws IOException {
        QueryBuilder builder = QueryBuilders.multiMatchQuery("superman再见jack","name","descript");
        testQuery(builder);
    }

    /**
     * matchPhraseQuery是以短语查询
     * 创建索引所使用的field的value中如果有这么一个短语（顺序无差，且连接在一起）
     * 才会被matchPhraseQuery查询出来
     */
    @Test
    public void testMatchPhraseQuery() throws IOException {
        QueryBuilder builder1 = QueryBuilders.matchPhraseQuery("descript","super");
        testQuery(builder1);
        System.out.println("=========================");
        QueryBuilder builder2 = QueryBuilders.matchPhraseQuery("descript","而抠");
        testQuery(builder2);
    }

    /**
     * 从matchQuery和termQuery的查询结果可以看出两者的差别
     * 默认情况下汉字通过termQuery查询时不会被分词(作为完整的词条)
     * 适合单个汉字或是个单词查询
     */
    @Test
    public void testTermQuery() throws IOException {
        QueryBuilder builder1 = QueryBuilders.termQuery("name","吕涵紫");
        testQuery(builder1);
        System.out.println("==========================");
        QueryBuilder builder2 = QueryBuilders.termQuery("name","紫");
        testQuery(builder2);
    }

    @Test
    public void testFilteredQuery() throws IOException {
        QueryBuilder builder = QueryBuilders.filteredQuery(QueryBuilders.termQuery("descript", "的"),
                FilterBuilders.rangeFilter("age")
                        .from(20)
                        .to(30)
                //.includeLower(true)
                //.includeUpper(false)
        );
        testQuery(builder);
    }

    /**
     * 指定type和索引的id查询
     */
    @Test
    public  void testIdsQuery() throws IOException {
        QueryBuilder builder = QueryBuilders.idsQuery("employees").ids("HP-0xff00");
        testQuery(builder);
    }

    public void testQuery(QueryBuilder queryBuilder) throws IOException {
        SearchResponse response = client.prepareSearch("company")
                .setTypes("employees")
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setQuery(queryBuilder)
                .setFilter(FilterBuilders.rangeFilter("age").from(20).to(30))
                .setFrom(0).setSize(20).setExplain(true)
                .execute().actionGet();
        SearchHits hits = response.getHits();
        System.out.println("Current results: " + hits.totalHits());
        for(SearchHit searchHit : hits){
            String json = searchHit.getSourceAsString();
            Employee employee = JacksonUtils.json2Obj(json, Employee.class);
            System.out.println(employee);
        }
    }

    @Test
    public void testDeleteIndexById(){
        DeleteResponse response = client.prepareDelete("company", "employees", "HP-0xff00")
                .execute().actionGet();
        assertThat("HP-0xff00",is(response.getId()));
        LOG.info("delete id : {}",response.getId());
    }

    @Test
    public void testDeleteIndexByQuery(){
        QueryBuilder query = QueryBuilders.fieldQuery("name", "jack");
        client.prepareDeleteByQuery("company").setQuery(query).execute().actionGet();
    }

    @After
    public void closeEsClient(){
        client = null;
        esClient.shutdown();
    }
}
