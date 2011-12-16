import org.junit.Test
import org.junit.Assert._

class LiftOFnTests {
  import PolyFun._
  import HList._
  import Tuples._
  import Functions._
  import LiftOFn._
  
  def typed[T](t : => T) {}

  @Test
  def testLiftO {
    
    val sum : (Int, Int) => Int = _ + _
    val prd : (Int, Int, Int) => Int = _ * _ * _
    
    val hlsum = sum.hlisted
    typed[Int :: Int :: HNil => Int](hlsum)
    
    val hlprd = prd.hlisted
    typed[Int :: Int :: Int :: HNil => Int](hlprd)
    
    val l1 = 2 :: 3 :: HNil
    val l2 = 2 :: 3 :: 4 :: HNil
    
    val s1 = hlsum(l1)
    assertEquals(5, s1)
    
    val p1 = hlprd(l2)
    assertEquals(24, p1)
    
    val l3 = Option(2) :: Option(3) :: HNil
    val isDef3 = l3.foldLeft(true)(isDefined)(_ & _)
    assertTrue(isDef3)
    val l3a = l3 map get
    val s2a = hlsum(l3a)
    assertEquals(5, s2a)

    val l4 = Option(2) :: Option(3) :: Option(4) :: HNil
    val isDef4 = l4.foldLeft(true)(isDefined)(_ & _)
    assertTrue(isDef4)
    val l4a = l4 map get
    val p2a = hlprd(l4a)
    assertEquals(24, p2a)

    val sumO = liftO(sum)
    typed[(Option[Int], Option[Int]) => Option[Int]](sumO)

    val s2 = sumO(Some(1), Some(2))
    assertTrue(s2.isDefined)
    assertEquals(3, s2.get)

    val s3 = sumO(Some(1), None)
    assertTrue(s3.isEmpty)
    
    val s4 = sumO(None, Some(2))
    assertTrue(s4.isEmpty)
    
    val s5 = sumO(None, None)
    assertTrue(s5.isEmpty)
    
    val s6 = List(Some(1), Some(2), Some(3), Some(4)).reduce(sumO)
    assertTrue(s6.isDefined)
    assertEquals(10, s6.get)
    
    val prdO = liftO(prd)
    typed[(Option[Int], Option[Int], Option[Int]) => Option[Int]](prdO)

    val p2 = prdO(Some(2), Some(3), Some(4))
    assertTrue(p2.isDefined)
    assertEquals(24, p2.get)

    val p3 = prdO(Some(2), None, Some(4))
    assertTrue(p3.isEmpty)

    val p4 = prdO(Some(2), Some(3), None)
    assertTrue(p4.isEmpty)
  }
}
