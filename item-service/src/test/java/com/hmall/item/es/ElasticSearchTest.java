package com.hmall.item.es;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.item.domain.po.Item;
import com.hmall.item.domain.po.ItemDoc;
import com.hmall.item.service.IItemService;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.naming.directory.SearchResult;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@SpringBootTest(properties = "spring.profiles.active=local")
public class ElasticSearchTest {

    private RestHighLevelClient client;

    @Test
    void testMatchAll() throws IOException {
        //1.创建request对象
        SearchRequest request = new SearchRequest("items");
        //2.配置request参数
        request.source()
                .query(QueryBuilders.boolQuery()
                        .must(QueryBuilders.matchQuery("name","脱脂牛奶"))
                        .filter(QueryBuilders.termQuery("brand","德亚"))
                        .filter(QueryBuilders.rangeQuery("price").lte(30000))
                );
        //3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        //4.解析结果
        parseResponseResult(response);

    }

    @Test
    void testSortAndPage() throws IOException {
        int pageNo=1,pageSize=5;
        //1.创建request对象
        SearchRequest request = new SearchRequest("items");
        //2.配置request参数
        request.source().query(QueryBuilders.matchAllQuery());
        request.source().from(pageNo-1).size(pageSize);
        request.source()
                .sort("sold", SortOrder.DESC)
                .sort("price", SortOrder.ASC);
        //3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        //4.解析结果
        parseResponseResult(response);

    }

    @Test
    void testHighlight() throws IOException {
        int pageNo=1,pageSize=5;
        //1.创建request对象
        SearchRequest request = new SearchRequest("items");
        //2.配置request参数
        request.source().query(QueryBuilders.matchQuery("name","脱脂牛奶"));
        request.source().highlighter(SearchSourceBuilder.highlight().field("name"));
        //3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        //4.解析结果
        parseResponseResult(response);

    }

    @Test
    void testAgg() throws IOException {
        //1.创建request对象
        SearchRequest request = new SearchRequest("items");
        //2.配置request参数
        request.source().size(0);
        String brandAggName="brandAgg";
        request.source().aggregation(
                AggregationBuilders.terms(brandAggName).field("brand").size(10)
        );
        //3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        //4.解析结果
        Aggregations aggregations = response.getAggregations();
        //4.1.根据聚合名称获取响应的聚合
        Terms brandTerms = aggregations.get(brandAggName);
        //4.2.获取buckets
        List<? extends Terms.Bucket> buckets = brandTerms.getBuckets();
        //4.3.遍历获取每一个bucket
        for (Terms.Bucket bucket : buckets) {
            System.out.println("brand:" + bucket.getKeyAsString());
            System.out.println("count:" + bucket.getDocCount());
        }
        
    }

    private static void parseResponseResult(SearchResponse response) {

        SearchHits searchHits = response.getHits();
        //4.1.总条数
        long total = searchHits.getTotalHits().value;
        System.out.println("total=" + total);
        //4.2.命中的数据
        SearchHit[] hits = searchHits.getHits();
        for (SearchHit hit : hits) {
            //4.2.1.获取source结果
            String json = hit.getSourceAsString();
            //4.2.2.转为ItemDoc
            ItemDoc doc = JSONUtil.toBean(json, ItemDoc.class);
            //4.3处理高亮结果
            Map<String, HighlightField> hfs = hit.getHighlightFields();
            if(hfs!=null && !hfs.isEmpty()){
                //4.3.1根据高亮字段获取高亮结果
                HighlightField hf = hfs.get("name");
                //4.3.2获取高亮结果，覆盖非高亮结果
                String hfName=hf.getFragments()[0].toString();
                doc.setName(hfName);
            }

            System.out.println("doc=" + doc);
        }
    }

    @BeforeEach
    void setup(){
        client=new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://192.168.100.128:9200")
        ));
    }

    @AfterEach
    void tearDown() throws IOException{
        if(client!=null){
            client.close();
        }
    }

}
