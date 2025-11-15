package com.hmall.item.es;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.item.domain.po.Item;
import com.hmall.item.domain.po.ItemDoc;
import com.hmall.item.service.IItemService;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

@SpringBootTest(properties = "spring.profiles.active=local")
public class ElasticDocumentTest {

    private RestHighLevelClient client;
    @Autowired
    private IItemService itemService;

    @Test
    void testIndexDoc() throws IOException {
        //0.准备文档数据
        //0.1.根据id查询数据库数据
        Item item=itemService.getById(100000011127L);
        //0.2.把数据库数据转为文档数据
        ItemDoc itemDoc= BeanUtil.copyProperties(item, ItemDoc.class);

        //1.准备Request
        IndexRequest request = new IndexRequest("items").id(itemDoc.getId());
        //2.准备请求参数
        request.source(JSONUtil.toJsonStr(itemDoc),XContentType.JSON);
        //3.发送请求
        IndexResponse response = client.index(request, RequestOptions.DEFAULT);

    }

    @Test
    void testGetDoc() throws IOException {
        //1.准备Request
        GetRequest request = new GetRequest("items", "100000011127");
        //2.发送请求
        GetResponse response=client.get(request,RequestOptions.DEFAULT);
        //3.解析响应结果
        String json=response.getSourceAsString();
        ItemDoc doc = JSONUtil.toBean(json, ItemDoc.class);
        System.out.println("doc="+doc);
    }

    @Test
    void testBulkDoc() throws IOException {
        //0.准备文档数据
        int pageNo=1,pageSize=500;
        while (true) {
            Page<Item> page=itemService.lambdaQuery()
                    .eq(Item::getStatus,1)
                    .page(Page.of(pageNo,pageSize));
            List<Item> records = page.getRecords();
            if(records==null||records.isEmpty()){
                return;
            }

            //1.准备Request
            BulkRequest request = new BulkRequest();
            //2.准备请求参数
            for (Item item : records) {
                request.add(new IndexRequest("items")
                        .id(item.getId().toString())
                        .source(JSONUtil.toJsonStr(BeanUtil.copyProperties(item,ItemDoc.class)),XContentType.JSON));
            }
            //3.发送请求
            client.bulk(request,RequestOptions.DEFAULT);
            //5.翻页
            pageNo++;
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
