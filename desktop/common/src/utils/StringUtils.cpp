#include "StringUtils.h"

#include <boost/algorithm/hex.hpp>
#include <boost/algorithm/string.hpp>
#include <cstdint>
#include <openssl/rand.h>

#ifdef WINDOWS
#include <Windows.h>
#endif

std::string StringUtils::Replace(const std::string &str, const std::string &from, const std::string &to) {
  if(from.empty())
    return str;
  std::string result = str;
  size_t startPos = 0;
  size_t toLen = to.empty() ? 1 : to.length();
  while((startPos = result.find(from, startPos)) != std::string::npos) {
    result.replace(startPos, from.length(), to);
    startPos += toLen;
  }
  return result;
}

std::vector<std::string> StringUtils::Split(const std::string &str, const std::string &delim) {
  size_t pos_start{}, pos_end{}, delim_len = delim.length();
  std::string token{};
  std::vector<std::string> res{};
  while((pos_end = str.find(delim, pos_start)) != std::string::npos) {
    token = str.substr(pos_start, pos_end - pos_start);
    pos_start = pos_end + delim_len;
    res.push_back(token);
  }
  res.push_back(str.substr(pos_start));
  return res;
}

std::string StringUtils::LTrim(const std::string &str) {
  auto result = str;
  result.erase(result.begin(), std::find_if(result.begin(), result.end(), [](unsigned char ch) { return !std::isspace(ch); }));
  return result;
}

std::string StringUtils::RTrim(const std::string &str) {
  auto result = str;
  result.erase(std::find_if(result.rbegin(), result.rend(), [](unsigned char ch) { return !std::isspace(ch); }).base(), result.end());
  return result;
}

std::string StringUtils::Trim(const std::string &str) {
  auto trim = RTrim(str);
  trim = LTrim(trim);
  return trim;
}

std::string StringUtils::ToLower(const std::string &str) {
  auto result = str;
  std::transform(result.begin(), result.end(), result.begin(), ::tolower);
  return result;
}

std::string StringUtils::Truncate(const std::string &str, uint32_t maxLen, const std::string &ellipsis) {
  auto result = str;
  if(str.length() > maxLen) {
    if(maxLen > ellipsis.length()) {
      return result.substr(0, maxLen - ellipsis.length()) + ellipsis;
    } else {
      return result.substr(0, maxLen);
    }
  }
  return result;
}

std::string StringUtils::RandomString(size_t len) {
  const std::string charset = "abcdefghijklmnopqrstuvwxyz"
                              "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                              "0123456789"
                              "!@#$%^&*()-_=+[]{}|;:,.<>?";
  std::vector<uint8_t> buffer(len);
  if(!RAND_bytes(buffer.data(), (int)len))
    return {};
  std::string result{};
  result.reserve(len);
  for(size_t i = 0; i < len; ++i)
    result += charset[buffer[i] % charset.size()];
  return result;
}

std::string StringUtils::WithSeperators(const std::string &str, const std::string &seperator, uint32_t groupSize) {
  std::vector<std::string> chunks{};
  for(size_t i = 0; i < str.size(); i += groupSize)
    chunks.push_back(str.substr(i, groupSize));
  return boost::algorithm::join(chunks, seperator);
}

std::vector<uint8_t> StringUtils::FromHexString(const std::string& hex) {
  std::vector<uint8_t> result{};
  result.reserve((hex.size() + 1) / 2);
  try {
    boost::algorithm::unhex(hex.begin(), hex.end(), std::back_inserter(result));
  } catch (const boost::algorithm::hex_decode_error&) {
    return {};
  }
  return result;
}

std::string StringUtils::ToHexString(const std::vector<uint8_t> &data) {
  std::string hex;
  boost::algorithm::hex(data.begin(), data.end(), std::back_inserter(hex));
  return hex;
}

std::string StringUtils::ToBase32String(const std::string &str) {
  const std::string BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  std::string encodedStr{};
  uint32_t buffer{};
  uint32_t bitsLeft{};
  for(uint8_t c : str) {
    buffer = (buffer << 8) | c;
    bitsLeft += 8;
    while(bitsLeft >= 5) {
      encodedStr += BASE32_ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1F];
      bitsLeft -= 5;
    }
  }
  if(bitsLeft > 0)
    encodedStr += BASE32_ALPHABET[(buffer << (5 - bitsLeft)) & 0x1F];
  return encodedStr;
}

// Italock: tira caracteres de controle (C0, DEL, C1) e invisiveis de direcao do texto (U+061C, U+200B..U+200F,
// U+2028..U+202E, U+2060..U+206F, U+FEFF): com U+202E um nome pode aparecer invertido na tela.
static std::string StripInvisible(const std::string &s) {
  std::string out{};
  out.reserve(s.size());
  auto at = [&](size_t i) { return i < s.size() ? static_cast<unsigned char>(s[i]) : static_cast<unsigned char>(0); };
  for(size_t i = 0; i < s.size();) {
    unsigned char c = at(i);
    if(c < 0x20 || c == 0x7F) { // C0 e DEL
      i++;
      continue;
    }
    if(c == 0xC2 && at(i + 1) >= 0x80 && at(i + 1) <= 0x9F) { // C1 (U+0080..U+009F)
      i += 2;
      continue;
    }
    if(c == 0xD8 && at(i + 1) == 0x9C) { // U+061C (marca de letra arabe)
      i += 2;
      continue;
    }
    if(c == 0xE2) {
      auto b1 = at(i + 1), b2 = at(i + 2);
      if((b1 == 0x80 && ((b2 >= 0x8B && b2 <= 0x8F) || (b2 >= 0xA8 && b2 <= 0xAE))) || (b1 == 0x81 && b2 >= 0xA0 && b2 <= 0xAF)) {
        i += 3;
        continue;
      }
    }
    if(c == 0xEF && at(i + 1) == 0xBB && at(i + 2) == 0xBF) { // U+FEFF
      i += 3;
      continue;
    }
    out.push_back(static_cast<char>(c));
    i++;
  }
  return out;
}

std::string StringUtils::SafeDeviceName(const std::string &input) {
  constexpr size_t MAX_DEVICE_NAME = 64; // bytes UTF-8
  auto out = StripInvisible(input);
  while(!out.empty() && (out.front() == ' ' || out.front() == '-'))
    out.erase(out.begin());
  if(out.size() > MAX_DEVICE_NAME) {
    size_t end = MAX_DEVICE_NAME;
    while(end > 0 && (static_cast<unsigned char>(out[end]) & 0xC0) == 0x80)
      end--;
    out.resize(end);
  }
  while(!out.empty() && out.back() == ' ')
    out.pop_back();
  return out;
}

#ifdef WINDOWS
std::wstring StringUtils::ToWideString(const std::string &string) {
  if(string.empty())
    return L"";
  const auto size_needed = MultiByteToWideChar(CP_UTF8, 0, &string.at(0), static_cast<int>(string.size()), nullptr, 0);
  if(size_needed <= 0)
    return L"";
  std::wstring result(size_needed, 0);
  MultiByteToWideChar(CP_UTF8, 0, &string.at(0), static_cast<int>(string.size()), &result.at(0), size_needed);
  return result;
}

std::string StringUtils::FromWideString(const std::wstring &wide_string) {
  if(wide_string.empty())
    return "";
  const auto size_needed = WideCharToMultiByte(CP_UTF8, 0, &wide_string.at(0), static_cast<int>(wide_string.size()), nullptr, 0, nullptr, nullptr);
  if(size_needed <= 0)
    return "";
  std::string result(size_needed, 0);
  WideCharToMultiByte(CP_UTF8, 0, &wide_string.at(0), static_cast<int>(wide_string.size()), &result.at(0), size_needed, nullptr, nullptr);
  return result;
}
#endif
