# NimbusRun




## Developer Notes
- Why no lombok in modules
  - Whatever magic lombok is doing it changes the byte code inside the jar so that It's difficult to debug if you compare it to the actual source code. Please see https://medium.com/javarevisited/the-lombok-bug-that-cost-us-6-hours-of-debugging-6be79a4ef9a3. 
- Why no Java platform Module System
> You're running into a known limitation of the Java Platform Module System (JPMS):
>
> Two modules cannot export or contain the same package.
> 
> In your case, both:
> 
> proto.google.cloud.compute.v1
> google.cloud.compute
> 
- Best you use sdkman 
  - `sdk use java 21.0.8-tem`


contain or export com.google.cloud.compute.v1, which causes a split package conflict.
## TODOS
- Separate compute implementations into separate modules using the java platform module system and create a jar for each one. All the jars  can be put into the same dockerfile. They should be lightweight enough where this will not be a significant problem. The reason for this approach is to avoid dependency conflicts in the future when more and more compute types are added.