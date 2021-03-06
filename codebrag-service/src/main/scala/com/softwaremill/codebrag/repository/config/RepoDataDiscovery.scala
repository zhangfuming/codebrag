package com.softwaremill.codebrag.repository.config

import java.nio.file.{Path, Paths}
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import com.softwaremill.codebrag.service.config.MultiRepoConfig

object RepoDataDiscovery {

  def discoverRepoDataFromConfig(repositoryConfig: MultiRepoConfig) = {
    val rootDir = resolveRootDir(repositoryConfig)
    discoverRepoNames(rootDir).map( repoName => {
      val repoLocation = discoverRepoLocation(rootDir, repoName)
      val repoType = discoverRepoType(repoLocation)
      val credentials = resolveCredentials(repoName, repositoryConfig)
      RepoData(repoLocation, repoName, repoType, credentials)
    })
  }

  private def resolveCredentials(repoName: String, repositoryConfig: MultiRepoConfig): Option[RepoCredentials] = {
    val repoSpecificCredentials = repositoryConfig.repositoriesConfig.get(repoName).getOrElse(repositoryConfig.globalConfig)
    val passphraseCredentials = repoSpecificCredentials.passphrase.map(PassphraseCredentials)
    val userPassCredentials = repoSpecificCredentials.userName.map { username =>
      val password = repoSpecificCredentials.password.getOrElse("")
      UserPassCredentials(username, password)
    }
    passphraseCredentials.orElse(userPassCredentials)
  }

  private def resolveRootDir(repositoryConfig: MultiRepoConfig) = {
    val rootDir = Paths.get(repositoryConfig.repositoriesRoot)
    if(!rootDir.toFile.isDirectory) {
      val path = rootDir.toFile.getCanonicalPath
      throw new RuntimeException(s"Cannot find base directory for repositories: ${path}")
    }
    rootDir
  }

  private def discoverRepoNames(rootDir: Path) = {
    val reposDirs = rootDir.toFile.listFiles.filter(_.isDirectory).map(_.getName).toList
    val reposRootDir = rootDir.toFile.getCanonicalPath
    if(reposDirs.isEmpty) throw new RuntimeException(s"Repositories not found in ${reposRootDir}. Please clone your repositories as a directory inside ${reposRootDir}")
    reposDirs
  }

  private def discoverRepoLocation(rootDir: Path, repoName: String) = {
    rootDir.resolve(repoName).toFile.getCanonicalPath
  }

  private def discoverRepoType(repoLocation: String) = {
    val SvnRepoSectionName = "svn-remote"
    val repo = new FileRepositoryBuilder().setGitDir(new File(repoLocation + File.separator + ".git")).setMustExist(true).build()
    import scala.collection.JavaConversions._
    val sections = repo.getConfig.getSections.toList
    if(sections.contains(SvnRepoSectionName)) {
      "git-svn"
    } else {
      "git"
    }
  }

}