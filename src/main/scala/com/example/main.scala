import scala.collection.mutable.ArrayBuffer
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import scala.concurrent.duration.DurationInt

object RecursiveActor {
  sealed trait Message
  case class CreateChild(name: String, depth: Int) extends Message
  case class AddNeighbor(neighbor: ActorRef[Message]) extends Message
  case class TrySendMessage() extends Message
  case class ChangeNeighbor(neighbor: ActorRef[Message]) extends Message
  case class ChangeN(n: Int) extends Message
  case class Decide() extends Message

  def apply(neighbors: Map[ActorRef[Message], Boolean] = Map.empty[ActorRef[Message], Boolean], ni: Int = 1): Behaviors.Receive[Message] = {
    var currentN = ni
    var currentNeighbors = neighbors
    var currentDepth = 0
    Behaviors.receive { (context, message) =>
      message match {
        
        case CreateChild(name, depth) if depth > 0 =>
          val childActor = context.spawn(RecursiveActor(), name) // Создаем дочерний узел
          context.log.info("Created node actor: {}", name)
          currentDepth = depth
          if(depth != 4){
            childActor ! AddNeighbor(context.self) // Добавляем дочернему узлу в качестве соседа текущий узел
          }          
          context.scheduleOnce(delay = 3.seconds, target = childActor, TrySendMessage()) // Пробуем отправить сообщение из дочернего узла родительскому                           
          
          context.self ! AddNeighbor(childActor) // Добавляем текущему узлу в качестве соседа дочерний узел
                    
          childActor ! CreateChild(name + "-n1", depth - 1)
          childActor ! CreateChild(name + "-n2", depth - 1) // Рекурсивное создание акторов с уменьшением глубины
                    
          Behaviors.same

        case ChangeNeighbor(neighbor) => 
          currentNeighbors = currentNeighbors + (neighbor -> true)
          Behaviors.same

        case ChangeN(n) =>
          currentN += n
          context.log.info("ni updated: {}", currentN)
          if(currentN == 15){
            context.self ! Decide()
          }
          else{
            context.scheduleOnce(delay = 3.seconds, target = context.self, TrySendMessage())
          }
          Behaviors.same

        case Decide() => 
          context.log.info("Recieved messeges from all nodes!")
          Behaviors.same

        case TrySendMessage() =>
          val nQuietNeighbors = currentNeighbors.count{ case (_, value) => !value } // Получаем число соседей, с которыми ещё не было взаимодействия
          context.log.info("Try sending msg. N of neighbors: {}", nQuietNeighbors)
          if (nQuietNeighbors == 1){
            val neighbor = currentNeighbors.find { case (_, value) => !value }.map(_._1).getOrElse(context.self) // Находим последний соседний узел без взаимодействия
            currentNeighbors = currentNeighbors + (neighbor -> true)
            neighbor ! ChangeNeighbor(neighbor = context.self)
            neighbor ! ChangeN(currentN)
          }
                      
          Behaviors.same
          
        case AddNeighbor(neighbor) =>
          currentNeighbors = currentNeighbors + (neighbor -> false) // Добавляем соседа в словарь со статусом false (с соседом ещё не было взаимодействия)
          Behaviors.same

        case _ =>
          Behaviors.same
      }
    }
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val system: ActorSystem[RecursiveActor.Message] = ActorSystem(RecursiveActor(), "recursive-actor-system")

    system ! RecursiveActor.CreateChild("node", 4)
    
    Thread.sleep(15000)
    
    system.terminate()
  }
}
