import java.util.*;
import java.util.concurrent.*;
import java.math.*;
import java.util.concurrent.atomic.AtomicIntegerArray;

class MultiRadix{

    int threadCount = Runtime.getRuntime().availableProcessors();
    int[][] allCount = new int[threadCount][];
	int[][] allb = new int[threadCount][];
	int[] sumb;
    int[] sumCount;
    int[] maxArr = new int[threadCount];
    int max;
    int mask;
    int n;
    int [] a;
    int [] b;
    int [] t;
	int [] delSum = new int[threadCount];
    CyclicBarrier sync; 
    int numBit = 2;
    int numDigits;
    int [] bit ;
    final static int NUM_BIT = 7;
	double[] paraTimes = new double[5];
	int aa = 0;

    public static void main(String [] args) {
	
	if (args.length != 1) {
	    System.out.println(" bruk : >java MultiRadix <n> ");
	} else {
	    int n = Integer.parseInt(args[0]);
		new MultiRadix().doIt(n);
	}
    } 
    
    void doIt (int len) {
		for(int j = 0; j < 5; j++){	
			a = new int[len];
			Random r = new Random(123);
			for (int i =0; i < len;i++) {
				a[i] = r.nextInt(len);
			}
			startThreads();
			aa++;
		}
		median("Parallel", paraTimes);
    } 

    void startThreads(){

	long startTime = System.nanoTime();
	n = a.length;
	sync = new CyclicBarrier(threadCount+1);

	for(int i = 0; i < threadCount; i++){
	    Thread t = new Thread(new Work(i, a));
	    t.start();
	}
	try{
	    sync.await();
	}
	catch(Exception e){return;}
	//Use local max to find global max
	max = localMax(maxArr,0,maxArr.length);
	while (max >= (1L<<numBit) )numBit++; 
	numDigits = Math.max(1, numBit/NUM_BIT);

	bit = new int[numDigits];
	int rest = (numBit%numDigits);
	
	for (int i = 0; i < bit.length; i++){
	    bit[i] = numBit/numDigits;
	    if ( rest-- > 0)  bit[i]++;
	}

	try{
	    sync.await();
	}
	catch(Exception e){return;}
	int[] t=a;
	b = new int [n];
		  
	int shift = 0;
	for(int i = 0; i<bit.length; i++){
	    
	    mask = (1<<bit[i]) -1;
	    sumCount = new int [mask+1];

	    try{
		sync.await();
	    }
	    catch(Exception e){return;}

	    try{
		sync.await();
	    }
	    catch(Exception e){return;}

	    try{
		sync.await();
	    }
	    catch(Exception e){return;}

	    try{
		sync.await();
	    }
	    catch(Exception e){return;}
		
	    t = a;
	    a = b;
	    b = t;

	    shift = shift+bit[i];
		try{
			sync.await();
	    }
	    catch(Exception e){return;}

	}
	double duration = (System.nanoTime() -startTime)/1000000.0;
	paraTimes[aa] = duration;
	System.out.println("\nSorterte "+n+" tall paa:" + duration + "millisek.");
	if (bit.length%2 != 0 ) {
	    System.arraycopy (a,0,b,0,a.length);
	}

	testSort(a);

    }

    class Work implements Runnable{

	int thread = 0;

	Work(int i, int[] arr){
	    thread = i;
	    a = arr;
	}
	
	public void run(){
	    
	    int limit = a.length/threadCount;
	    int start = thread*limit;
	    int end = start+limit;
	    int numBit = 2;
	    int numDigits;
	    int n =a.length;
	    int sum = 0;
	    if(thread+1 == threadCount){
		end = a.length;
	    }
	    maxArr[thread] = localMax(a, start, end);
	    try{
		sync.await();
	    }
	    catch(Exception e){return;}

	    try{
		sync.await();
	    }
	    catch(Exception e){return;}

	    
	    for(int i = 0; i < bit.length; i++){
			try{
				sync.await();
			}
			catch(Exception e){return;}

			radixSort(a, b, bit[i], sum, thread);
		
			sum+=bit[i];
		    try{
				sync.await();
			}
			catch(Exception e){return;}
		

	    }
	}
    }
	//Find local max
    int localMax(int[] a, int start, int end){
	int locmax = 0;
	for(int i = start; i < end; i++){
	    if (a[i] > locmax) locmax = a[i];
	}
	return locmax;
    }

    void radixSort ( int [] a, int [] b, int maskLen, int shift, int thread){
	int [] count = new int [mask+1];
	int [] localCount = new int[count.length];
	int limit = a.length/threadCount;
	int start = thread*limit;
	int end = start+limit;
	if(thread+1 == threadCount){
	    end = a.length;
	}
	allCount[thread] = findCount(a,start,end,maskLen, shift);

	try{
	    sync.await();
	}
	catch(Exception e){return;}

	fillSumCount(thread);
	try{
	    sync.await();
	}
	catch(Exception e){return;}

	localCount = partC(sumCount, allCount, thread);
	partD(localCount, b, a, start, end, shift);
	try{
	    sync.await();
	}
		catch(Exception e){return;}

    }
	
	//Part D
	void partD (int[] localCount, int[] b, int[] a, int start, int end, int shift){
		for(int i = start; i<end; i++){
			 b[localCount[(a[i]>>>shift) & mask]++] = a[i];
		}
	}
	
	//Part C
	int[] partC(int[] sum, int[][]c, int thread){
		int[] localCount = new int[c[0].length];
		//int t = 0;
		int acum = 0;
		int i = -1;
		for(int r = 0; r < sum.length; r++){
			if(i >= 0){
				acum += sumCount[i];
				localCount[r] = acum;
			}
			for(int s = 0; s<thread; s++){
				localCount[r] += allCount[s][r];
				
			}
			i++;
		}
		return localCount;
	}
	

	//Find sumCount
    void fillSumCount(int thread){
	int limit = allCount[0].length/threadCount;
	int start = thread*limit;
	int end = start+limit;
	if(thread+1 == threadCount){
	    end = allCount[0].length;
	}
	for(int i = 0; i < threadCount; i++){
	    for(int j = start; j < end; j++){
	        
		sumCount[j] = sumCount[j] + allCount[i][j];
	    }
	}
    }
	void median(String aa, double[] arr){
	Arrays.sort(arr);
	System.out.println("\nMedian time of "+ aa + " = " + arr[2] + " millisek.");
    }


	//Find this thread's count array and returns it into the thread's index in allCount
    int[] findCount(int[] a, int start, int end, int maskLen, int shift){
	
	int mask = (1<<maskLen) -1;
	int [] count = new int [mask+1];
	for (int i = start; i < end; i++){
	    count[(a[i]>> shift) & mask]++;;
	}
	return count;
    }
	
    void testSort(int [] a){
	for (int i = 0; i< a.length-1;i++) {
	    if (a[i] > a[i+1]){
		System.out.println("SorteringsFEIL på plass: "+i +" a["+i+"]:"+a[i]+" > a["+(i+1)+"]:"+a[i+1]);
		return;
	    }
	}
    }
}
