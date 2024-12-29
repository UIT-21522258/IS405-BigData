import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class ProbProduct {
    public static class ProductPairMapper extends Mapper<Object, Text, Text, IntWritable> {

        private final static IntWritable one = new IntWritable(1);
        private Text productPair = new Text();
    
        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            String[] products = value.toString().split(" ");
            
            // Tạo cặp sản phẩm (i, j) và (i, *)
            for (int i = 0; i < products.length; i++) {
                String productA = products[i];
                
                for (int j = i + 1; j < products.length; j++) {
                    String productB = products[j];
    
                    productPair.set(productA + "," + productB);
                    context.write(productPair, one);
    
                    productPair.set(productA + ",*");
                    context.write(productPair, one);
                }
            }
        }
    }

    public static class SumCombiner extends Reducer<Text, IntWritable, Text, IntWritable> {
        private IntWritable result = new IntWritable();
    
        @Override
        public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable val : values) {
                sum += val.get();
            }
            result.set(sum);
            context.write(key, result);
        }
    }
    
    public static class ProductPairReducer extends Reducer<Text, IntWritable, Text, Text> {

        private Map<String, Integer> productACountMap = new HashMap<>();
    
        @Override
        public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            String pair = key.toString();
            String[] products = pair.split(",");
            String productA = products[0];
            String productB = products[1];
    
            int sum = 0;
            for (IntWritable value : values) {
                sum += value.get();
            }
    
            // Nếu là cặp A,*
            if (productB.equals("*")) {
                productACountMap.put(productA, sum);
            } else {
                // Nếu là cặp A,B, tính xác suất
                if (productACountMap.containsKey(productA)) {
                    int countA = productACountMap.get(productA);
                    double probability = (double) sum / countA;
                    context.write(new Text(pair), new Text("P(" + productB + "|" + productA + ") = " + probability));
                }
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "product pair probability");


        job.setJarByClass(ProbProduct.class);
        job.setMapperClass(ProductPairMapper.class);
        job.setCombinerClass(SumCombiner.class);
        job.setReducerClass(ProductPairReducer.class);


        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);


        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));


        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }

}
