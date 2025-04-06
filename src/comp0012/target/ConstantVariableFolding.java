package comp0012.target;

public class ConstantVariableFolding
{
    public int methodOne(){
        int a = 62;
        int b = (a + 764) * 3;
        return b + 1234 - a;
    }

    public double methodTwo(){
        double i = 0.67;
        int j = 1;
        return i + j;
    }

    public boolean methodThree(){
        int x = 12345;
        int y = 54321;
        return x > y;
    }

    public boolean methodFour(){
        // int a = 0;
        // if (1 > a) {
        //     a = 1;
        // } else if (2 > a){
        //     a = 1;
        // } else {
        //     a = 1;
        // }
        // a = 2;
        // return true;
        long x = 4835783423L;
        long y = 400000;
        long z = x + y;
        return x > y;
    }

}