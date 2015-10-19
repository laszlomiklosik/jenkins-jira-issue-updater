package info.bluefloyd.jira.model;

/**
 * Simple REST result holder.
 * 
 * @author Ian Sparkes, Swisscom AG
 */
public class RestResult {
  private Integer resultCode;
  private String  resultMessage;
  private boolean validResult;

  /**
   * @return the resultCode
   */
  public Integer getResultCode() {
    return resultCode;
  }

  /**
   * @param resultCode the resultCode to set
   */
  public void setResultCode(Integer resultCode) {
    this.resultCode = resultCode;
  }

  /**
   * @return the resultMessage
   */
  public String getResultMessage() {
    return resultMessage;
  }

  /**
   * @param resultMessage the resultMessage to set
   */
  public void setResultMessage(String resultMessage) {
    this.resultMessage = resultMessage;
  }

  /**
   * @return the validResult
   */
  public boolean isValidResult() {
    return validResult;
  }

  /**
   * @param validResult the validResult to set
   */
  public void setValidResult(boolean validResult) {
    this.validResult = validResult;
  }
}
