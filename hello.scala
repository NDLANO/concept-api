
// main function
// no need for "=" since there is no return type specified

object Test
{
    def main(args: Array[String]) : Unit =
    {
        println("Hello World")
        println(Add.addInt(1,2))
    }
}

object Add
{
    def addInt(a:Int, b:Int) : Int = 
    {
        var sum = a + b
        return sum
    }
}