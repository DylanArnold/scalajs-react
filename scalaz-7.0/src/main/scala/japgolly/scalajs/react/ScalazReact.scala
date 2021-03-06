package japgolly.scalajs.react

import org.scalajs.dom
import vdom.ReactVDom._

import scala.scalajs.js.{UndefOr, undefined}
import scalaz._
import Scalaz.Id
import scalaz.effect.IO

object ScalazReact {
  // Don't edit this directly. Run sync-scala70

  implicit val IoToIo: IO ~> IO = NaturalTransformation.refl[IO]
  implicit object IdToIo extends (Id ~> IO) {
    override def apply[A](a: Id[A]): IO[A] = IO(a)
  }

  implicit final class SzRExt_Attr(val a: Attr) extends AnyVal {

    def ~~>(io: IO[Unit]) =
      a --> io.unsafePerformIO()

    def ~~>[N <: dom.Node, E <: SyntheticEvent[N]](eventHandler: E => IO[Unit]) =
      a.==>[N, E](eventHandler(_).unsafePerformIO())
  }

  implicit final class SzRExt_C_M(val u: ComponentScope_M) extends AnyVal {
    def forceUpdateIO = IO(u.forceUpdate())
  }

  implicit final class SzRExt_SEvent[N <: dom.Node](val e: SyntheticEvent[N]) extends AnyVal {
    /**
     * Stops the default action of an element from happening.
     * For example: Prevent a submit button from submitting a form Prevent a link from following the URL
     */
    def preventDefaultIO = IO(e.preventDefault())
    /**
     * Stops the bubbling of an event to parent elements, preventing any parent event handlers from being execUnsafeuted.
     */
    def stopPropagationIO = IO(e.stopPropagation())
  }

  val preventDefaultIO  = (_: SyntheticEvent[dom.Node]).preventDefaultIO
  val stopPropagationIO = (_: SyntheticEvent[dom.Node]).stopPropagationIO

  // ===================================================================================================================
  // State manipulation

  final type OpCallbackIO = UndefOr[IO[Unit]]
  implicit def OpCallbackFromIO(cb: OpCallbackIO): OpCallback = cb.map(f => () => f.unsafePerformIO())

  @inline final def StateAndCallbacks[S](s: S, cb: OpCallbackIO = undefined) = new StateAndCallbacks[S](s, cb)
  final class StateAndCallbacks[S](val s: S, val cb: OpCallbackIO) {
    @inline def withState(s2: S) = new StateAndCallbacks(s2, cb)
    @inline def addCallback(cb2: OpCallbackIO) = new StateAndCallbacks(s, appendCallbacks(cb, cb2))
  }

  @inline final private def appendCallbacks(a: OpCallbackIO, b: OpCallbackIO): OpCallbackIO =
    a.fold(b)(aa => b.fold(aa)(bb => aa.flatMap(_ => bb)))

  final type ReactS[S, A] = ReactST[Id, S, A]
  final type ReactST[M[+_], S, A] = StateT[M, StateAndCallbacks[S], A]

  object ReactS {
    @inline final def apply[S, A](f: S => (S, A)): ReactS[S, A] = applyT[Id, S, A](f)

    @inline final def ret[S, A](a: A): ReactS[S, A] = retT[Id, S, A](a)

    @inline final def get[S]: ReactS[S, S] = gets(identity[S])

    @inline final def gets[S, A](f: S => A): ReactS[S, A] =
      State.gets[StateAndCallbacks[S], A](s => f(s.s))

    @inline final def set[S](s: S): ReactS[S, Unit] = mod((_: S) => s)

    @inline final def mod[S](f: S => S): ReactS[S, Unit] = modT[Id, S](f)

    @inline final def callback[S, A](c: OpCallbackIO)(a: A): ReactS[S, A] =
      State[StateAndCallbacks[S], A](s => (s addCallback c, a))

    @inline final def applyT[M[+_], S, A](f: S => M[(S, A)])(implicit F: Functor[M]): ReactST[M, S, A] =
      StateT[M, StateAndCallbacks[S], A](sc => F.map(f(sc.s))(x => (sc withState x._1, x._2) ))

    @inline final def retT[M[+_], S, A](a: A)(implicit M: Applicative[M]): ReactST[M, S, A] =
      StateT[M, StateAndCallbacks[S], A](s => M.point((s, a)))

    @inline final def retM[M[+_], S, A](ma: M[A])(implicit F: Functor[M]): ReactST[M, S, A] = getsT[M, S, A](_ => ma)

    @inline final def getT[M[+_]: Applicative, S]: ReactST[M, S, S] = get.lift[M]

    @inline final def getsT[M[+_], S, A](f: S => M[A])(implicit F: Functor[M]): ReactST[M, S, A] =
      StateT[M, StateAndCallbacks[S], A](sc => F.map(f(sc.s))((sc, _)))

    @inline final def setT[M[+_]: Applicative, S](s: S): ReactST[M, S, Unit] = set(s).lift[M]

    @inline final def modT[M[+_], S](f: S => M[S])(implicit M: Functor[M]): ReactST[M, S, Unit] =
      StateT[M, StateAndCallbacks[S], Unit](sc => M.map(f(sc.s))(s2 => (sc withState s2,()) ))

    @inline final def callbackT[M[+_]: Applicative, S, A](c: OpCallbackIO)(a: A): ReactST[M, S, A] = callback(c)(a).lift[M]

    @inline final def Fix[S] = new Fix[S]
    final class Fix[S] {
      @inline final def apply[A](f: S => (S, A))           = ReactS(f)
      @inline final def ret[A](a: A)                       = ReactS.ret[S,A](a)
      @inline final def get                                = ReactS.get[S]
      @inline final def gets[A](f: S => A)                 = ReactS.gets(f)
      @inline final def set(s: S)                          = ReactS.set(s)
      @inline final def mod(f: S => S)                     = ReactS.mod(f)
      @inline final def callback[A](c: OpCallbackIO)(a: A) = ReactS.callback[S,A](c)(a)
      @inline final def applyT[M[+_]: Functor, A](f: S => M[(S, A)])            = ReactS.applyT(f)
      @inline final def retT[M[+_]: Applicative, A](a: A)                       = ReactS.retT[M,S,A](a)
      @inline final def retM[M[+_]: Functor, A](ma: M[A])                       = ReactS.retM[M,S,A](ma)
      @inline final def getT[M[+_]: Applicative]                                = ReactS.getT[M,S]
      @inline final def getsT[M[+_]: Applicative, A](f: S => M[A])              = ReactS.getsT(f)
      @inline final def setT[M[+_]: Applicative](s: S)                          = ReactS.setT[M,S](s)
      @inline final def modT[M[+_]: Functor](f: S => M[S])                      = ReactS.modT[M,S](f)
      @inline final def callbackT[M[+_]: Applicative, A](c: OpCallbackIO)(a: A) = ReactS.callbackT[M,S,A](c)(a)
    }

    @inline final def FixT[M[+_], S] = new FixT[M,S]
    final class FixT[M[+_], S] {
      @inline final def apply[A](f: S => M[(S, A)])       (implicit M: Functor[M])     = ReactS.applyT(f)
      @inline final def ret[A](a: A)                      (implicit M: Applicative[M]) = ReactS.retT[M,S,A](a)
      @inline final def retM[A](ma: M[A])                 (implicit M: Functor[M])     = ReactS.retM[M,S,A](ma)
      @inline final def get                               (implicit M: Applicative[M]) = ReactS.getT[M,S]
      @inline final def gets[A](f: S => M[A])             (implicit M: Functor[M])     = ReactS.getsT(f)
      @inline final def set(s: S)                         (implicit M: Applicative[M]) = ReactS.setT[M,S](s)
      @inline final def mod(f: S => M[S])                 (implicit M: Functor[M])     = ReactS.modT[M,S](f)
      @inline final def callback[A](c: OpCallbackIO)(a: A)(implicit M: Applicative[M]) = ReactS.callbackT[M,S,A](c)(a)
    }

    @inline final def lift[M[+_], S, A](t: StateT[M, S, A])(implicit M: Functor[M]): ReactST[M, S, A] =
      StateT[M, StateAndCallbacks[S], A](sc => M.map(t(sc.s))(sa => (sc withState sa._1, sa._2) ))

    @inline final def unlift[M[+_], S, A](t: ReactST[M, S, A])(implicit M: Functor[M]): StateT[M, S, A] =
      StateT[M, S, A](s => M.map(t(StateAndCallbacks(s)))(sa => (sa._1.s, sa._2) ))
  }

  implicit final class SzRExt_ReactSTOps[M[+_], S, A](val f: ReactST[M,S,A]) extends AnyVal {
    def addCallback(c: OpCallbackIO)(implicit M: Monad[M]): ReactST[M,S,A] =
      f flatMap ReactS.callbackT(c)

    // This shouldn't be needed; it's already in BindSyntax.
    def >>[B](t: ReactST[M,S,B])(implicit M: Bind[M]): ReactST[M,S,B] =
      f.flatMap(_ => t)
  }

  implicit final class SzRExt_CompStateAccessOps[C[_], S](val u: C[S]) extends AnyVal {
    type CC = CompStateAccess[C]

    @inline private def run[M[+_], A, B](st: ReactST[M, S, A], f: (S, S, A, => IO[Unit]) => IO[B])(implicit C: CC, M: M ~> IO): IO[B] =
      IO(StateAndCallbacks(C state u)).flatMap(s1 =>
        M(st run s1).flatMap { case (s2, a) =>
          f(s1.s, s2.s, a, IO(C.setState(u, s2.s, s2.cb)))
        }
      )

    def runState[M[+_], A](st: ReactST[M, S, A])(implicit C: CC, M: M ~> IO): IO[A] =
      run[M, A, A](st, (s1,s2,a,io) => io.map(_ => a))

    def _runState[I, M[+_], A](f: I => ReactST[M, S, A])(implicit C: CC, M: M ~> IO): I => IO[A] =
      i => runState(f(i))

    def _runState[I, M[+_], A](f: I => ReactST[M, S, A], cb: I => OpCallbackIO)(implicit C: CC, M: M ~> IO, N: Monad[M]): I => IO[A] =
      i => runState(f(i) addCallback cb(i))

    def runStateS[M[+_], A](st: StateT[M, S, A])(implicit C: CC, M: M ~> IO, N: Functor[M]): IO[A] =
      runState(ReactS lift st)

    def _runStateS[I, M[+_], A](f: I => StateT[M, S, A])(implicit C: CC, M: M ~> IO, N: Functor[M]): I => IO[A] =
      i => runStateS(f(i))

    def runStateF[M[+_], A](st: ReactST[M, S, A])(implicit C: CC, M: M ~> IO, F: ChangeFilter[S]): IO[A] =
      run[M, A, A](st, (s1,s2,a,io) => if (F.allowChange(s1,s2)) io.map(_ => a) else IO(a))

    def _runStateF[I, M[+_], A](f: I => ReactST[M, S, A])(implicit C: CC, M: M ~> IO, F: ChangeFilter[S]): I => IO[A] =
      i => runStateF(f(i))

    def _runStateF[I, M[+_], A](f: I => ReactST[M, S, A], cb: I => OpCallbackIO)(implicit C: CC, M: M ~> IO, N: Monad[M], F: ChangeFilter[S]): I => IO[A] =
      i => runStateF(f(i) addCallback cb(i))

    def runStateFS[M[+_], A](st: StateT[M, S, A])(implicit C: CC, M: M ~> IO, N: Functor[M], F: ChangeFilter[S]): IO[A] =
      runStateF(ReactS lift st)

    def _runStateFS[I, M[+_], A](f: I => StateT[M, S, A])(implicit C: CC, M: M ~> IO, N: Functor[M], F: ChangeFilter[S]): I => IO[A] =
      i => runStateFS(f(i))
  }

  case class ChangeFilter[S](allowChange: (S, S) => Boolean)
  object ChangeFilter {
    def refl[S] = apply[S](_ != _)
    def reflOn[S, T](f: S => T) = apply[S](f(_) != f(_))
    def equal[S: Equal] = apply[S]((a,b) => !implicitly[Equal[S]].equal(a,b))
    def equalOn[S, T: Equal](f: S => T) = apply[S]((a,b) => !implicitly[Equal[T]].equal(f(a),f(b)))
  }

  // Seriously, Scala, get your shit together.
  @inline final implicit def moarScalaHandHolding[P,S](b: BackendScope[P,S]): SzRExt_CompStateAccessOps[ComponentScope_SS, S] = (b: ComponentScope_SS[S])
  @inline final implicit def moarScalaHandHolding[P,S,B](b: ComponentScopeU[P,S,B]): SzRExt_CompStateAccessOps[ComponentScope_SS, S] = (b: ComponentScope_SS[S])
}
