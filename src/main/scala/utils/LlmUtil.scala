package alex.demo
package utils

object LlmUtil:
  val geminiApiKey: String = sys.env("GEMINI_API_KEY")
  def call(prompt: String): Option[String] =
    // http call with retries
    None
  end call

end LlmUtil
