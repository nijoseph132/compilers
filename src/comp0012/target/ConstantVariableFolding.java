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
        long x = 4835783423L;
        long y = 400000;
        long z = x + y;
        return x > y;
    }

    public int methodFive() {
        int x = 10;
        int y = x * 5 + 3;
        int z = (y - 8) / 2;
        return z % 7;
    }

    public long methodSix() {
        long a = 2147483647L; // MAX_INT
        long b = a * 2L;
        long c = b + 1000L;
        return c / 3L;
    }

    public float methodSeven() {
        float f1 = 3.14f;
        float f2 = 2.71f;
        float f3 = (f1 + f2) * 0.5f;
        return f3 / 1.5f;
    }

    public double methodEight() {
        double d1 = 1.0;
        double d2 = 3.0;
        double d3 = (d1 / d2) * 9.0;
        return d3 + 0.0001;
    }

    public double methodNine() {
        int i = 100;
        float f = 2.5f;
        double d = 3.333;
        return i * f + d / 2.0;
    }

    public double methodTen() {
        double a = 1.0;
        double b = 2.0;
        double c = 3.0;
        double d = 4.0;
        return (a * b) + (c / d) - Math.pow(a, b) + Math.sqrt(c);
    }

    public int methodEleven() {
        int a = 10;
        int b = 10 + a;
        int c = 10 + b;
        int d = 10 + c;
        return 10 + d;
    }
}